package com.clinic.backend.storage;

import com.clinic.backend.crypto.AesGcmCipher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Chiffrement applicatif des fichiers uploadés au repos (D3b) — AES-256-GCM, au-delà
 * du chiffrement de volume. Le contenu binaire (photos patients, images d'imagerie,
 * logo) est chiffré sur disque ({@code MAGIC ‖ IV ‖ ciphertext+tag}) et déchiffré à la
 * lecture par {@link FileStorageService}.
 *
 * <p><b>Clé & rotation.</b> La clé courante dérive de {@code app.storage.encryption.key}
 * (par défaut {@code app.encryption.key}). Pour une rotation, on fournit l'ancienne clé
 * via {@code app.storage.encryption.previous-key} : à la lecture, on tente la clé courante
 * puis l'ancienne (repli). La route de maintenance {@link #rotateAll(Path)} relit chaque
 * fichier (clé courante, ancienne, ou clair legacy) et le ré-écrit chiffré avec la clé
 * courante — une fois la rotation jouée, l'ancienne clé peut être retirée de la config.
 *
 * <p>Une clé de fichier <b>dédiée</b> (et non la clé maître {@code app.encryption.key}
 * directement) découple la rotation des fichiers de celle des colonnes PHI (D3a/P2.5) :
 * tourner la clé des fichiers ne rend jamais la base illisible.
 */
@Service
@Slf4j
public class FileEncryptionService {

    private final AesGcmCipher current;
    private final AesGcmCipher previous; // null si aucune rotation en cours

    public FileEncryptionService(
            @Value("${app.storage.encryption.key:${app.encryption.key}}") String key,
            @Value("${app.storage.encryption.previous-key:}") String previousKey) {
        this.current = new AesGcmCipher(key);
        this.previous = (previousKey != null && !previousKey.isBlank()) ? new AesGcmCipher(previousKey) : null;
    }

    /** Chiffre des octets avec la clé courante. */
    public byte[] encrypt(byte[] plaintext) {
        return current.encryptBytes(plaintext);
    }

    /**
     * Déchiffre des octets : clé courante d'abord, puis l'ancienne clé en repli (si
     * configurée). Un contenu sans marqueur est renvoyé tel quel (fichier clair legacy).
     */
    public byte[] decrypt(byte[] data) {
        if (data == null) {
            return null;
        }
        if (!AesGcmCipher.isEncryptedBytes(data)) {
            return data; // fichier legacy en clair
        }
        try {
            return current.decryptBytes(data);
        } catch (RuntimeException withCurrent) {
            if (previous != null) {
                try {
                    return previous.decryptBytes(data);
                } catch (RuntimeException ignored) {
                    // ni la clé courante ni l'ancienne → on relance l'erreur d'origine
                }
            }
            throw withCurrent;
        }
    }

    /**
     * Re-chiffre tous les fichiers sous {@code root} avec la clé courante (rotation).
     * Relit chaque fichier (clé courante / ancienne / clair legacy) puis le ré-écrit
     * chiffré ; chiffre aussi au passage d'éventuels fichiers encore en clair.
     *
     * @return nombre de fichiers traités
     */
    public int rotateAll(Path root) {
        if (root == null || !Files.exists(root)) {
            return 0;
        }
        int[] count = {0};
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile).forEach(f -> {
                try {
                    byte[] clear = decrypt(Files.readAllBytes(f));
                    Files.write(f, encrypt(clear));
                    count[0]++;
                } catch (IOException e) {
                    throw new UncheckedIOException("Échec de rotation du fichier : " + f, e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Échec du parcours du répertoire de stockage : " + root, e);
        }
        log.info("Rotation de chiffrement des fichiers : {} fichier(s) re-chiffré(s) sous {}", count[0], root);
        return count[0];
    }
}
