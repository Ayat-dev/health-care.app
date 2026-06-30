package com.clinic.backend.security;

import com.clinic.backend.dto.RefreshSessionDto;
import com.clinic.backend.model.RefreshToken;
import com.clinic.backend.model.User;
import com.clinic.backend.repository.RefreshTokenRepository;
import com.clinic.backend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * Cycle de vie des refresh tokens (P4.4) : émission, rotation, révocation.
 *
 * <p>Modèle : le brut (256 bits aléatoires) est rendu au client une seule fois ; seul son
 * SHA-256 est stocké. La <b>rotation</b> révoque l'ancien jeton à chaque usage et en émet un
 * nouveau. La <b>réutilisation</b> d'un jeton déjà révoqué trahit un vol (le client légitime
 * et l'attaquant détiennent le même brut) → on révoque toute la lignée ET on incrémente la
 * version de token pour invalider aussi les access tokens en cours.
 */
@Service
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final long refreshExpirationMs;
    private final long cleanupRetentionDays;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               UserRepository userRepository,
                               @Value("${app.jwt.refresh-expiration-ms}") long refreshExpirationMs,
                               @Value("${app.jwt.refresh-cleanup-retention-days}") long cleanupRetentionDays) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.refreshExpirationMs = refreshExpirationMs;
        this.cleanupRetentionDays = cleanupRetentionDays;
    }

    /** Émet un nouveau refresh token et renvoie sa valeur BRUTE (non stockée). */
    @Transactional
    public String issue(User user) {
        return issue(user, null, null);
    }

    /**
     * Émet un refresh token en estampillant les métadonnées d'appareil (D1c) pour la vue
     * admin « sessions actives ». Renvoie la valeur BRUTE (non stockée).
     */
    @Transactional
    public String issue(User user, String userAgent, String ipAddress) {
        String raw = generateRaw();
        RefreshToken token = new RefreshToken(
                user.getId(), sha256(raw),
                LocalDateTime.now().plusNanos(refreshExpirationMs * 1_000_000));
        token.setUserAgent(truncate(userAgent));
        token.setIpAddress(ipAddress);
        token.setLastUsedAt(LocalDateTime.now());
        refreshTokenRepository.save(token);
        return raw;
    }

    /**
     * Valide et fait tourner un refresh token : révoque l'ancien, en émet un nouveau.
     * Renvoie le contexte (utilisateur + nouveau brut) pour reforger un access token.
     *
     * @throws IllegalArgumentException jeton inconnu, expiré, ou réutilisation détectée
     */
    @Transactional
    public RotationResult rotate(String rawToken) {
        return rotate(rawToken, null, null);
    }

    /**
     * Variante avec métadonnées d'appareil (D1c) : la session conserve son identité
     * (user-agent reporté) à travers la rotation, et {@code last_used_at} est rafraîchi.
     */
    @Transactional
    public RotationResult rotate(String rawToken, String userAgent, String ipAddress) {
        RefreshToken existing = refreshTokenRepository.findByTokenHash(sha256(rawToken))
                .orElseThrow(() -> new IllegalArgumentException("Refresh token invalide."));

        User user = userRepository.findById(existing.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Refresh token invalide."));

        // Jeton déjà révoqué : deux cas distincts.
        //  • révoqué PAR ROTATION (replacedById != null) et rejoué = vol présumé (l'attaquant et
        //    le client légitime détiennent le même brut) → on coupe toute la lignée + version.
        //  • révoqué SANS remplacement (admin « révoquer cette session » / logout) = jeton mort
        //    sans rejeu suspect → simple 401, SANS escalade (sinon révoquer un appareil tuerait
        //    aussi les autres sessions légitimes de l'utilisateur).
        if (existing.getRevokedAt() != null) {
            if (existing.getReplacedById() != null) {
                log.warn("Réutilisation d'un refresh token rotaté (user {}) — révocation de toute la lignée.",
                        user.getUsername());
                revokeAllForUser(user);
            }
            throw new IllegalArgumentException("Refresh token invalide.");
        }
        if (existing.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Refresh token expiré.");
        }
        if (!user.isEnabled() || !user.isAccountNonLocked()) {
            throw new IllegalArgumentException("Compte indisponible.");
        }

        String newRaw = generateRaw();
        RefreshToken rotated = new RefreshToken(
                user.getId(), sha256(newRaw),
                LocalDateTime.now().plusNanos(refreshExpirationMs * 1_000_000));
        // L'identité de l'appareil suit la chaîne ; à défaut on garde celle du jeton tournant.
        rotated.setUserAgent(truncate(userAgent != null ? userAgent : existing.getUserAgent()));
        rotated.setIpAddress(ipAddress != null ? ipAddress : existing.getIpAddress());
        rotated.setCreatedAt(existing.getCreatedAt()); // âge de la session = début de la chaîne
        rotated.setLastUsedAt(LocalDateTime.now());
        refreshTokenRepository.save(rotated);

        existing.setRevokedAt(LocalDateTime.now());
        existing.setReplacedById(rotated.getId());

        return new RotationResult(user, newRaw);
    }

    /** Logout : révoque le refresh token courant (silencieux si inconnu/déjà révoqué). */
    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(sha256(rawToken)).ifPresent(t -> {
            if (t.getRevokedAt() == null) t.setRevokedAt(LocalDateTime.now());
        });
    }

    /**
     * Révocation TOTALE des sessions d'un utilisateur (logout-all, désactivation, vol) :
     * révoque tous ses refresh tokens actifs ET incrémente sa version de token, ce qui
     * invalide immédiatement ses access tokens encore valides.
     */
    @Transactional
    public void revokeAllForUser(User user) {
        List<RefreshToken> active = refreshTokenRepository.findByUserIdAndRevokedAtIsNull(user.getId());
        LocalDateTime now = LocalDateTime.now();
        active.forEach(t -> t.setRevokedAt(now));
        user.bumpTokenVersion();
        userRepository.save(user);
        log.info("Révocation de toutes les sessions de {} ({} refresh tokens, tv={}).",
                user.getUsername(), active.size(), user.getTokenVersion());
    }

    /**
     * D1b — purge les refresh tokens expirés ou révoqués depuis plus de
     * {@code app.jwt.refresh-cleanup-retention-days} jours. Tenant-agnostique
     * (les refresh tokens ne sont pas {@code @TenantId}) → aucun {@code runAs}.
     *
     * @return nombre de jetons supprimés
     */
    @Transactional
    public int purgeStaleTokens() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(cleanupRetentionDays);
        int deleted = refreshTokenRepository.deleteExpiredOrRevokedBefore(cutoff);
        if (deleted > 0) {
            log.info("Purge des refresh tokens : {} jeton(s) expiré(s)/révoqué(s) avant {} supprimé(s).",
                    deleted, cutoff);
        }
        return deleted;
    }

    /**
     * D1c — sessions actives (refresh tokens utilisables) d'un utilisateur, pour la vue admin.
     * N'expose jamais le jeton, seulement l'id (cible de révocation) + les métadonnées.
     */
    @Transactional(readOnly = true)
    public List<RefreshSessionDto> listActiveForUser(Long userId) {
        return refreshTokenRepository
                .findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastUsedAtDesc(userId, LocalDateTime.now())
                .stream().map(this::toSessionDto).toList();
    }

    /**
     * D1c — révocation CIBLÉE d'une session (un appareil), par id de refresh token. L'appareil
     * ne pourra plus rafraîchir (son prochain /refresh → 401) ; son access token courant expire
     * dans son TTL court (pas de blocklist par jeton — révocation immédiate de TOUT l'utilisateur
     * = logout-all / bump de version). {@code expectedUserId} garde-fou : la session doit bien
     * appartenir à l'utilisateur ciblé. Idempotent.
     *
     * @return true si une session active a été révoquée
     */
    @Transactional
    public boolean revokeSession(Long tokenId, Long expectedUserId) {
        return refreshTokenRepository.findById(tokenId)
                .filter(t -> t.getUserId().equals(expectedUserId))
                .filter(t -> t.getRevokedAt() == null)
                .map(t -> {
                    t.setRevokedAt(LocalDateTime.now());
                    log.info("Session révoquée (refresh token {}, user {}).", tokenId, expectedUserId);
                    return true;
                })
                .orElse(false);
    }

    private RefreshSessionDto toSessionDto(RefreshToken t) {
        RefreshSessionDto dto = new RefreshSessionDto();
        dto.setId(t.getId());
        dto.setUserAgent(t.getUserAgent());
        dto.setIpAddress(t.getIpAddress());
        dto.setCreatedAt(t.getCreatedAt());
        dto.setLastUsedAt(t.getLastUsedAt());
        dto.setExpiresAt(t.getExpiresAt());
        return dto;
    }

    private String truncate(String value) {
        if (value == null) return null;
        return value.length() > 256 ? value.substring(0, 256) : value;
    }

    private String generateRaw() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    /** Résultat d'une rotation : utilisateur résolu + nouveau refresh token brut. */
    public record RotationResult(User user, String newRefreshToken) {}
}
