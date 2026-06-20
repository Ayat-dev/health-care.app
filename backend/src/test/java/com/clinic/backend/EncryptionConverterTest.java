package com.clinic.backend;

import com.clinic.backend.crypto.AesGcmCipher;
import com.clinic.backend.patient.Patient;
import com.clinic.backend.patient.PatientRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Chiffrement PHI au repos (P2.5).
 *
 * <p>Volet unitaire : invariants AES-GCM ({@link AesGcmCipher}) — aller-retour,
 * IV aléatoire, tolérance legacy, détection d'altération.
 *
 * <p>Volet intégration : un patient sauvé via JPA stocke ses colonnes sensibles
 * **chiffrées en base** (lecture brute par {@link JdbcTemplate}) tout en restituant
 * le texte clair via l'entité (le convertisseur déchiffre à la relecture).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EncryptionConverterTest {

    @Autowired PatientRepository patientRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired EntityManager entityManager;

    // ── AesGcmCipher (unitaire, sans Spring) ─────────────────────────────────

    private final AesGcmCipher cipher = new AesGcmCipher("une-clef-de-test-quelconque");

    @Test
    void aller_retour_restitue_le_texte_clair() {
        String clear = "Pénicilline, arachides — allergie sévère";
        String encrypted = cipher.encrypt(clear);

        assertThat(encrypted).startsWith("gcm:").doesNotContain(clear);
        assertThat(cipher.decrypt(encrypted)).isEqualTo(clear);
    }

    @Test
    void null_reste_null() {
        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.decrypt(null)).isNull();
    }

    @Test
    void iv_aleatoire_chaque_chiffrement() {
        assertThat(cipher.encrypt("HTA")).isNotEqualTo(cipher.encrypt("HTA"));
    }

    @Test
    void valeur_en_clair_legacy_renvoyee_telle_quelle() {
        // Donnée pré-existante sans marqueur gcm: → considérée déjà en clair.
        assertThat(cipher.decrypt("ancien texte non chiffré")).isEqualTo("ancien texte non chiffré");
    }

    @Test
    void alteration_du_chiffre_detectee() {
        String encrypted = cipher.encrypt("Diabète type 2");
        String tampered = encrypted.substring(0, encrypted.length() - 2) + "AA";
        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cle_differente_ne_dechiffre_pas() {
        String encrypted = cipher.encrypt("Drépanocytose");
        AesGcmCipher autre = new AesGcmCipher("une-autre-clef");
        assertThatThrownBy(() -> autre.decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── Intégration JPA : colonnes chiffrées en base ─────────────────────────

    @Test
    void colonnes_phi_chiffrees_en_base_mais_claires_via_jpa() {
        Patient p = new Patient();
        p.setRecordNumber("ENC-TEST-0001");
        p.setFirstName("Test");
        p.setLastName("Chiffrement");
        p.setMedicalHistory("Antécédent confidentiel : HTA depuis 2019");
        p.setAllergies("Pénicilline");
        Long id = patientRepository.saveAndFlush(p).getId();
        entityManager.clear(); // force une relecture depuis la base

        // Lecture BRUTE : la valeur stockée est chiffrée (marqueur gcm:, pas de clair).
        String rawHistory = jdbcTemplate.queryForObject(
                "SELECT medical_history FROM patients WHERE id = ?", String.class, id);
        assertThat(rawHistory).startsWith("gcm:").doesNotContain("HTA");

        // Lecture via JPA : le convertisseur déchiffre → texte clair restitué.
        Patient reloaded = patientRepository.findById(id).orElseThrow();
        assertThat(reloaded.getMedicalHistory()).isEqualTo("Antécédent confidentiel : HTA depuis 2019");
        assertThat(reloaded.getAllergies()).isEqualTo("Pénicilline");
    }
}
