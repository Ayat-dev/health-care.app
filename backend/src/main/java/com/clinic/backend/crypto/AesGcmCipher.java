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

    /** Marqueur binaire (4 octets « GCM1 ») en tête des fichiers chiffrés (D3b). Ne
     *  collisionne pas avec les en-têtes JPEG/PNG/WebP → distingue clair vs chiffré. */
    static final byte[] FILE_MAGIC = {'G', 'C', 'M', '1'};

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

    // ── Variante binaire pour les fichiers au repos (D3b) ────────────────────
    // Format : MAGIC(4) ‖ IV(12) ‖ ciphertext+tag. IV aléatoire par fichier.

    /** {@code true} si {@code data} porte le marqueur {@link #FILE_MAGIC} (donc chiffré). */
    public static boolean isEncryptedBytes(byte[] data) {
        if (data == null || data.length < FILE_MAGIC.length) return false;
        for (int i = 0; i < FILE_MAGIC.length; i++) {
            if (data[i] != FILE_MAGIC[i]) return false;
        }
        return true;
    }

    /** Chiffre des octets bruts. {@code null} → {@code null}. */
    public byte[] encryptBytes(byte[] plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);

            byte[] out = new byte[FILE_MAGIC.length + iv.length + ciphertext.length];
            System.arraycopy(FILE_MAGIC, 0, out, 0, FILE_MAGIC.length);
            System.arraycopy(iv, 0, out, FILE_MAGIC.length, iv.length);
            System.arraycopy(ciphertext, 0, out, FILE_MAGIC.length + iv.length, ciphertext.length);
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("Échec du chiffrement de fichier PHI.", e);
        }
    }

    /**
     * Déchiffre des octets produits par {@link #encryptBytes}. Une donnée sans le
     * marqueur {@link #FILE_MAGIC} est considérée déjà en clair (fichier legacy) et
     * renvoyée telle quelle. Une donnée chiffrée avec une autre clé lève
     * {@link IllegalStateException} (tag GCM invalide) — exploité pour le repli de
     * clé lors de la rotation (D3b).
     */
    public byte[] decryptBytes(byte[] data) {
        if (data == null) {
            return null;
        }
        if (!isEncryptedBytes(data)) {
            return data;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(data, FILE_MAGIC.length, iv, 0, IV_LENGTH);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            int offset = FILE_MAGIC.length + IV_LENGTH;
            byte[] plaintext = cipher.doFinal(data, offset, data.length - offset);
            return plaintext;
        } catch (Exception e) {
            throw new IllegalStateException("Échec du déchiffrement de fichier PHI (clé incorrecte ou donnée altérée ?).", e);
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
