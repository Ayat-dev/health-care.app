package com.clinic.backend;

import com.clinic.backend.consultation.Consultation;
import com.clinic.backend.consultation.ConsultationRepository;
import com.clinic.backend.dto.EpidemiologyReportDto;
import com.clinic.backend.model.User;
import com.clinic.backend.patient.Patient;
import com.clinic.backend.patient.PatientRepository;
import com.clinic.backend.reports.ReportService;
import com.clinic.backend.repository.UserRepository;
import com.clinic.backend.tenant.ClinicRepository;
import com.clinic.backend.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chiffrement PHI clinique au repos (D3a) — narratifs de consultation (dont le
 * diagnostic) + résultats de labo. Prouve que :
 * <ol>
 *   <li>la colonne {@code diagnosis} est chiffrée en base (lecture JDBC brute = {@code gcm:…})
 *       mais restituée en clair via l'entité ;</li>
 *   <li>l'agrégation « top pathologies » (épidémiologie) repose sur les codes CIM-10
 *       structurés <b>non chiffrés</b> (D4c) — comptage par code après découpage des chaînes
 *       multi-codes, et résolution du libellé « CODE — Titre ».</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ClinicalPhiEncryptionTest {

    @Autowired ConsultationRepository consultationRepository;
    @Autowired PatientRepository patientRepository;
    @Autowired ReportService reportService;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired EntityManager entityManager;
    @Autowired ClinicRepository clinicRepository;

    private User doctor() {
        return userRepository.findByUsername("dr.martin").orElseThrow();
    }

    @BeforeTransaction
    void setTenant() {
        TenantContext.set(clinicRepository.findByCodeIgnoreCase("CENTRALE").orElseThrow().getId());
    }

    @AfterTransaction
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void diagnostic_chiffre_en_base_clair_via_jpa() {
        Patient p = patientRepository.findAll().get(0);
        Consultation c = new Consultation();
        c.setPatient(p);
        c.setDoctor(doctor());
        c.setConsultationDate(LocalDateTime.now());
        c.setStatus("TERMINE");
        c.setChiefComplaint("Céphalées depuis 3 jours");
        c.setDiagnosis("Paludisme simple");
        c.setTreatmentPlan("ACT 3 jours");
        Long id = consultationRepository.saveAndFlush(c).getId();
        entityManager.clear();

        String raw = jdbcTemplate.queryForObject(
                "SELECT diagnosis FROM consultations WHERE id = ?", String.class, id);
        assertThat(raw).startsWith("gcm:").doesNotContain("Paludisme");

        String rawComplaint = jdbcTemplate.queryForObject(
                "SELECT chief_complaint FROM consultations WHERE id = ?", String.class, id);
        assertThat(rawComplaint).startsWith("gcm:").doesNotContain("Céphalées");

        Consultation reloaded = consultationRepository.findById(id).orElseThrow();
        assertThat(reloaded.getDiagnosis()).isEqualTo("Paludisme simple");
        assertThat(reloaded.getChiefComplaint()).isEqualTo("Céphalées depuis 3 jours");
        assertThat(reloaded.getTreatmentPlan()).isEqualTo("ACT 3 jours");
    }

    @Test
    void top_pathologies_agregees_sur_les_codes_cim10() {
        Patient p = patientRepository.findAll().get(0);
        // Codes CIM-10 (champ structuré non chiffré). B54 dans 2 consultations (dont 1 chaîne
        // multi-codes « B54, J45 »), J45 dans 2 consultations → l'agrégat doit compter B54=2 et
        // J45=2 : preuve que la chaîne multi-codes est bien découpée (sinon « B54, J45 » formerait
        // son propre groupe à 1 et B54 seul vaudrait 1).
        seed(p, "B54");
        seed(p, "B54, J45");
        seed(p, "J45");
        consultationRepository.flush();
        entityManager.clear();

        LocalDate today = LocalDate.now();
        EpidemiologyReportDto report = reportService.epidemiology(today.getMonthValue(), today.getYear());

        // Le libellé est « CODE — Titre » (titre résolu depuis le catalogue CIM-10).
        var b54 = report.getTopPathologies().stream()
                .filter(lv -> lv.getLabel() != null && lv.getLabel().startsWith("B54"))
                .findFirst().orElseThrow();
        assertThat(b54.getLabel()).contains("Paludisme");
        assertThat(b54.getCount()).isEqualTo(2L);
        var j45 = report.getTopPathologies().stream()
                .filter(lv -> lv.getLabel() != null && lv.getLabel().startsWith("J45"))
                .findFirst().orElseThrow();
        assertThat(j45.getCount()).isEqualTo(2L);
    }

    private void seed(Patient p, String icd10Codes) {
        Consultation c = new Consultation();
        c.setPatient(p);
        c.setDoctor(doctor());
        c.setConsultationDate(LocalDateTime.now());
        c.setStatus("TERMINE");
        c.setDiagnosis("Diagnostic narratif"); // texte libre toujours présent (et chiffré)
        c.setIcd10Codes(icd10Codes);
        consultationRepository.save(c);
    }
}
