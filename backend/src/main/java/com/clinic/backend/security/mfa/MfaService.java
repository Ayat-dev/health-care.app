package com.clinic.backend.security.mfa;

import com.clinic.backend.config.ResourceNotFoundException;
import com.clinic.backend.model.User;
import com.clinic.backend.repository.UserRepository;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.util.Utils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * MFA/2FA par TOTP (Tier E3, opt-in, chaîne web session). Enrôlement (secret + QR), confirmation,
 * vérification (TOTP ou code de secours), désactivation, reset admin. La clé TOTP est chiffrée au
 * repos (converter PHI) ; les codes de secours sont hachés (BCrypt), à usage unique.
 */
@Service
@RequiredArgsConstructor
public class MfaService {

    private static final String ISSUER = "ClinicApp";
    private static final int RECOVERY_CODE_COUNT = 8;
    /** Alphabet sans caractères ambigus (0/O, 1/I/L). */
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();

    private final UserRepository userRepository;
    private final MfaRecoveryCodeRepository recoveryCodeRepository;
    private final PasswordEncoder passwordEncoder;

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final CodeVerifier codeVerifier =
            new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());
    private final QrGenerator qrGenerator = new ZxingPngQrGenerator();
    private final SecureRandom random = new SecureRandom();

    /** Données d'enrôlement : le secret (à saisir à la main) + le QR (data-URI inline). */
    public record SetupData(String secret, String qrDataUri) { }

    /** Démarre l'enrôlement : génère un secret et le persiste (MFA reste désactivé jusqu'à confirmation). */
    @Transactional
    public SetupData beginSetup(Long userId) {
        User user = get(userId);
        String secret = secretGenerator.generate();
        user.setMfaSecret(secret);
        user.setMfaEnabled(false);
        userRepository.save(user);
        return new SetupData(secret, qrDataUri(user.getUsername(), secret));
    }

    /** Confirme l'enrôlement avec un code TOTP ; active le MFA et renvoie les codes de secours (une fois). */
    @Transactional
    public List<String> confirmSetup(Long userId, String code) {
        User user = get(userId);
        if (user.getMfaSecret() == null) {
            throw new IllegalStateException("Aucune configuration MFA en cours.");
        }
        if (!isValidTotp(user.getMfaSecret(), code)) {
            throw new IllegalArgumentException("Code invalide.");
        }
        user.setMfaEnabled(true);
        userRepository.save(user);
        return regenerateRecoveryCodes(userId);
    }

    /** Désactive le MFA de l'utilisateur (self-service) et supprime ses codes de secours. */
    @Transactional
    public void disable(Long userId) {
        User user = get(userId);
        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        userRepository.save(user);
        recoveryCodeRepository.deleteByUserId(userId);
    }

    /** Reset par un administrateur (l'utilisateur a perdu son appareil et ses codes). */
    @Transactional
    public void adminReset(Long userId) {
        disable(userId);
    }

    /** Vérifie un code au login : TOTP (6 chiffres) OU code de secours (consommé). */
    @Transactional
    public boolean verify(Long userId, String code) {
        User user = get(userId);
        if (!user.isMfaEnabled() || user.getMfaSecret() == null) return false;
        String c = code == null ? "" : code.trim();
        if (c.matches("\\d{6}") && isValidTotp(user.getMfaSecret(), c)) return true;
        return consumeRecoveryCode(userId, c);
    }

    public boolean isEnabled(Long userId) {
        return get(userId).isMfaEnabled();
    }

    public long remainingRecoveryCodes(Long userId) {
        return recoveryCodeRepository.countByUserIdAndUsedAtIsNull(userId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────
    private boolean isValidTotp(String secret, String code) {
        return code != null && codeVerifier.isValidCode(secret, code.trim());
    }

    private boolean consumeRecoveryCode(Long userId, String code) {
        String normalized = code.replace("-", "").replace(" ", "").toUpperCase();
        if (normalized.isEmpty()) return false;
        for (MfaRecoveryCode rc : recoveryCodeRepository.findByUserIdAndUsedAtIsNull(userId)) {
            if (passwordEncoder.matches(normalized, rc.getCodeHash())) {
                rc.setUsedAt(LocalDateTime.now());
                recoveryCodeRepository.save(rc);
                return true;
            }
        }
        return false;
    }

    private List<String> regenerateRecoveryCodes(Long userId) {
        recoveryCodeRepository.deleteByUserId(userId);
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String code = randomCode();               // ex. « ABCD-2345 » (affiché)
            codes.add(code);
            String normalized = code.replace("-", ""); // haché sans le tiret
            recoveryCodeRepository.save(new MfaRecoveryCode(userId, passwordEncoder.encode(normalized)));
        }
        return codes;
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(9);
        for (int i = 0; i < 8; i++) {
            if (i == 4) sb.append('-');
            sb.append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]);
        }
        return sb.toString();
    }

    private String qrDataUri(String username, String secret) {
        QrData data = new QrData.Builder()
                .label(username)
                .secret(secret)
                .issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();
        try {
            return Utils.getDataUriForImage(qrGenerator.generate(data), qrGenerator.getImageMimeType());
        } catch (QrGenerationException e) {
            throw new IllegalStateException("Échec de génération du QR MFA : " + e.getMessage(), e);
        }
    }

    private User get(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable : " + userId));
    }
}
