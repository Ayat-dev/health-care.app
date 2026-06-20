package com.clinic.backend.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Chiffrement symétrique AES-256-GCM pour les données PHI au repos (P2.5).
 *
 * <p>Format de stockage : {@code gcm:<base64(IV ‖ ciphertext+tag)>}.
 * L'IV (12 octets) est tiré aléatoirement à chaque chiffrement → deux chiffrés
 * du même texte clair diffèrent (pas de fuite par égalité). Le tag GCM (128 bits)
 * authentifie le contenu (toute altération du chiffré fait échouer le déchiffrement).
 *
 * <p>La clé AES (32 octets) est dérivée par SHA-256 du secret de configuration, ce
 * qui accepte n'importe quel secret de longueur arbitraire sans contrainte de format
 * (Base64, passphrase…). En production, fournir un secret aléatoire fort via
 * {@code APP_ENCRYPTION_KEY} (cf. {@code .env.example}).
 *
 * <p>Classe pure (sans dépendance Spring) → testable en isolation. Thread-safe : un
 * {@link Cipher} est instancié par appel (les instances JCE ne sont pas réutilisables
 * de façon concurrente).
 */
public final class AesGcmCipher {

    /** Marqueur de chiffré — permet de tolérer d'anciennes valeurs en clair au déchiffrement. */
    static final String PREFIX = "gcm:";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;       // 96 bits, recommandé pour GCM
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public AesGcmCipher(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("Le secret de chiffrement (app.encryption.key) est requis.");
        }
        this.key = new SecretKeySpec(sha256(secret), "AES");
    }

    /** Chiffre une valeur. {@code null} → {@code null}. */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] out = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ciphertext, 0, out, iv.length, ciphertext.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Échec du chiffrement PHI.", e);
        }
    }

    /**
     * Déchiffre une valeur. {@code null} → {@code null}. Une valeur sans le marqueur
     * {@link #PREFIX} est considérée comme déjà en clair (legacy / seed SQL) et renvoyée
     * telle quelle — garantit une montée en charge sans casse sur des données existantes.
     */
    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        if (!stored.startsWith(PREFIX)) {
            return stored;
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(all, 0, iv, 0, IV_LENGTH);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plaintext = cipher.doFinal(all, IV_LENGTH, all.length - IV_LENGTH);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Échec du déchiffrement PHI (clé incorrecte ou donnée altérée ?).", e);
        }
    }

    private static byte[] sha256(String secret) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 indisponible.", e);
        }
    }
}
