package com.clinic.backend.security;

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
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               UserRepository userRepository,
                               @Value("${app.jwt.refresh-expiration-ms}") long refreshExpirationMs) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    /** Émet un nouveau refresh token et renvoie sa valeur BRUTE (non stockée). */
    @Transactional
    public String issue(User user) {
        String raw = generateRaw();
        RefreshToken token = new RefreshToken(
                user.getId(), sha256(raw),
                LocalDateTime.now().plusNanos(refreshExpirationMs * 1_000_000));
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
        RefreshToken existing = refreshTokenRepository.findByTokenHash(sha256(rawToken))
                .orElseThrow(() -> new IllegalArgumentException("Refresh token invalide."));

        User user = userRepository.findById(existing.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Refresh token invalide."));

        // Réutilisation d'un jeton déjà révoqué = vol présumé → on coupe tout.
        if (existing.getRevokedAt() != null) {
            log.warn("Réutilisation d'un refresh token révoqué (user {}) — révocation de toute la lignée.",
                    user.getUsername());
            revokeAllForUser(user);
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
