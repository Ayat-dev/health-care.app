package com.clinic.backend;

import com.clinic.backend.storage.FileEncryptionService;
import com.clinic.backend.storage.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Chiffrement des fichiers uploadés au repos + rotation de clé (D3b).
 *
 * <p>Pur Java (sans Spring) : {@link FileEncryptionService} et {@link FileStorageService}
 * sont instanciables directement (clé en argument de constructeur).
 */
class FileEncryptionTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};

    // ── Stockage : contenu illisible sur disque, restitué en clair via load() ─────

    @Test
    void fichier_stocke_chiffre_sur_disque_mais_clair_a_la_lecture(@TempDir Path tmp) throws Exception {
        FileEncryptionService enc = new FileEncryptionService("cle-fichiers", "");
        FileStorageService storage = new FileStorageService(tmp.toString(), enc);

        MockMultipartFile upload = new MockMultipartFile(
                "file", "photo.png", "image/png", PNG);
        String webPath = storage.storeImage(upload, "patients/42");

        // Sur disque : marqueur « GCM1 », et surtout PAS l'en-tête PNG d'origine.
        Path onDisk = tmp.resolve(webPath.substring("/uploads/".length()));
        byte[] raw = Files.readAllBytes(onDisk);
        assertThat(new String(raw, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("GCM1");
        assertThat(raw).isNotEqualTo(PNG);

        // Via load() : déchiffré → octets d'origine + type MIME.
        FileStorageService.StoredFile loaded = storage.load(webPath);
        assertThat(loaded).isNotNull();
        assertThat(loaded.content()).isEqualTo(PNG);
        assertThat(loaded.contentType()).isEqualTo("image/png");
    }

    @Test
    void fichier_legacy_en_clair_lu_tel_quel(@TempDir Path tmp) throws Exception {
        FileEncryptionService enc = new FileEncryptionService("cle-fichiers", "");
        FileStorageService storage = new FileStorageService(tmp.toString(), enc);

        // Fichier pré-existant écrit en clair (avant D3b).
        Path dir = tmp.resolve("patients/7");
        Files.createDirectories(dir);
        Files.write(dir.resolve("legacy.png"), PNG);

        FileStorageService.StoredFile loaded = storage.load("/uploads/patients/7/legacy.png");
        assertThat(loaded.content()).isEqualTo(PNG);
    }

    // ── Rotation de clé : relit (ancienne/courante/clair) puis ré-écrit (courante) ─

    @Test
    void rotation_re_chiffre_avec_la_nouvelle_cle(@TempDir Path tmp) throws Exception {
        byte[] data = "contenu confidentiel".getBytes(StandardCharsets.UTF_8);

        // 1) Fichier chiffré avec l'ANCIENNE clé.
        FileEncryptionService oldOnly = new FileEncryptionService("ANCIENNE", "");
        Path f = tmp.resolve("img.bin");
        Files.write(f, oldOnly.encrypt(data));

        // 2) Service en transition : nouvelle clé courante + ancienne en repli.
        FileEncryptionService rotating = new FileEncryptionService("NOUVELLE", "ANCIENNE");
        assertThat(rotating.decrypt(Files.readAllBytes(f))).isEqualTo(data); // lu via repli

        // 3) Rotation : ré-écrit avec la clé courante.
        assertThat(rotating.rotateAll(tmp)).isEqualTo(1);

        // 4) La nouvelle clé seule suffit désormais ; l'ancienne ne peut plus lire.
        FileEncryptionService newOnly = new FileEncryptionService("NOUVELLE", "");
        byte[] rotated = Files.readAllBytes(f);
        assertThat(newOnly.decrypt(rotated)).isEqualTo(data);
        assertThatThrownBy(() -> oldOnly.decrypt(rotated))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rotation_chiffre_aussi_les_fichiers_clairs_legacy(@TempDir Path tmp) throws Exception {
        Files.write(tmp.resolve("legacy.png"), PNG);

        FileEncryptionService enc = new FileEncryptionService("cle", "");
        assertThat(enc.rotateAll(tmp)).isEqualTo(1);

        byte[] raw = Files.readAllBytes(tmp.resolve("legacy.png"));
        assertThat(new String(raw, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("GCM1");
        assertThat(enc.decrypt(raw)).isEqualTo(PNG);
    }

    @Test
    void rotation_repertoire_absent_renvoie_zero() {
        FileEncryptionService enc = new FileEncryptionService("cle", "");
        assertThat(enc.rotateAll(Path.of("repertoire-inexistant-" + System.nanoTime()))).isZero();
    }

    @Test
    void plusieurs_fichiers_comptes(@TempDir Path tmp) throws Exception {
        FileEncryptionService enc = new FileEncryptionService("cle", "");
        for (String name : List.of("a.png", "b.png", "c.png")) {
            Files.write(tmp.resolve(name), PNG);
        }
        assertThat(enc.rotateAll(tmp)).isEqualTo(3);
    }
}
