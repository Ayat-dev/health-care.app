package com.clinic.backend;

import com.clinic.backend.audit.AuditLog;
import com.clinic.backend.audit.AuditLogRepository;
import com.clinic.backend.catalog.ActCatalogRepository;
import com.clinic.backend.clinicconfig.ClinicConfig;
import com.clinic.backend.clinicconfig.ClinicConfigService;
import com.clinic.backend.department.DepartmentRepository;
import com.clinic.backend.radiology.RadiologyExamCatalogRepository;
import com.clinic.backend.hospitalization.HospitalizationRepository;
import com.clinic.backend.hospitalization.RoomRepository;
import com.clinic.backend.maternity.MaternityRecordRepository;
import com.clinic.backend.notification.NotificationRepository;
import com.clinic.backend.patient.Patient;
import com.clinic.backend.patient.PatientRepository;
import com.clinic.backend.pharmacy.StockItemRepository;
import com.clinic.backend.radiology.RadiologyRequestRepository;
import com.clinic.backend.tenant.ClinicRepository;
import com.clinic.backend.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Multi-tenant à discriminant (P4.2) — preuve de cloisonnement des données cliniques
 * entre cliniques (tenants), via {@code @TenantId} + {@link TenantContext}.
 * <p>
 * Les données seedées : CENTRALE (clinic1) porte « Aminata Diallo » (PAT-2026-00001) ;
 * PLATEAU (clinic2) porte « Awa Bah » (PAT-PLT-00001).
 */
@SpringBootTest
@ActiveProfiles("test")
class MultiTenancyTest {

    @Autowired PatientRepository patientRepository;
    @Autowired ClinicRepository clinicRepository;
    @Autowired StockItemRepository stockItemRepository;
    @Autowired MaternityRecordRepository maternityRecordRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired RadiologyRequestRepository radiologyRequestRepository;
    @Autowired RoomRepository roomRepository;
    @Autowired HospitalizationRepository hospitalizationRepository;
    @Autowired ClinicConfigService clinicConfigService;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired ActCatalogRepository actCatalogRepository;
    @Autowired RadiologyExamCatalogRepository radiologyExamCatalogRepository;

    private Long clinic1() { return clinicRepository.findByCodeIgnoreCase("CENTRALE").orElseThrow().getId(); }
    private Long clinic2() { return clinicRepository.findByCodeIgnoreCase("PLATEAU").orElseThrow().getId(); }

    @AfterEach
    void clear() { TenantContext.clear(); }

    @Test
    void chaque_clinique_ne_voit_que_ses_propres_patients() {
        List<String> dansClinic1 = TenantContext.callAs(clinic1(),
                () -> patientRepository.findAll().stream().map(Patient::getRecordNumber).toList());
        List<String> dansClinic2 = TenantContext.callAs(clinic2(),
                () -> patientRepository.findAll().stream().map(Patient::getRecordNumber).toList());

        assertThat(dansClinic1).contains("PAT-2026-00001").doesNotContain("PAT-PLT-00001");
        assertThat(dansClinic2).contains("PAT-PLT-00001").doesNotContain("PAT-2026-00001");
    }

    @Test
    void acces_par_id_cloisonne_entre_cliniques() {
        Long p1Id = TenantContext.callAs(clinic1(),
                () -> patientRepository.findByRecordNumberAndDeletedAtIsNull("PAT-2026-00001")
                        .orElseThrow().getId());

        // Le même id n'est PAS atteignable depuis l'autre clinique (filtre @TenantId sur le find).
        assertThat(TenantContext.callAs(clinic2(), () -> patientRepository.findById(p1Id))).isEmpty();
        assertThat(TenantContext.callAs(clinic1(), () -> patientRepository.findById(p1Id))).isPresent();
    }

    @Test
    void stock_maternite_imagerie_cloisonnes_par_clinique() {
        // CENTRALE (clinic1) porte stock + dossier maternité + demandes d'imagerie seedés ; PLATEAU (clinic2) aucun.
        long stockC1 = TenantContext.callAs(clinic1(), () -> stockItemRepository.count());
        long stockC2 = TenantContext.callAs(clinic2(), () -> stockItemRepository.count());
        long matC1   = TenantContext.callAs(clinic1(), () -> maternityRecordRepository.count());
        long matC2   = TenantContext.callAs(clinic2(), () -> maternityRecordRepository.count());
        long radC1   = TenantContext.callAs(clinic1(), () -> radiologyRequestRepository.count());
        long radC2   = TenantContext.callAs(clinic2(), () -> radiologyRequestRepository.count());
        // CENTRALE porte les 8 chambres (seedées V10, rétro-remplies V28) + 1 séjour ; PLATEAU aucune.
        long roomC1  = TenantContext.callAs(clinic1(), () -> roomRepository.count());
        long roomC2  = TenantContext.callAs(clinic2(), () -> roomRepository.count());
        long hospC1  = TenantContext.callAs(clinic1(), () -> hospitalizationRepository.count());
        long hospC2  = TenantContext.callAs(clinic2(), () -> hospitalizationRepository.count());

        assertThat(stockC1).isGreaterThan(0);
        assertThat(stockC2).isZero();
        assertThat(matC1).isGreaterThan(0);
        assertThat(matC2).isZero();
        assertThat(radC1).isGreaterThan(0);
        assertThat(radC2).isZero();
        assertThat(roomC1).isGreaterThan(0);
        assertThat(roomC2).isZero();
        assertThat(hospC1).isGreaterThan(0);
        assertThat(hospC2).isZero();
    }

    @Test
    void notifications_et_audit_cloisonnes_par_clinique() {
        // 4 notifications seedées en CENTRALE (clinic1) ; PLATEAU (clinic2) n'en a aucune.
        assertThat(TenantContext.callAs(clinic1(), () -> notificationRepository.count())).isGreaterThan(0);
        assertThat(TenantContext.callAs(clinic2(), () -> notificationRepository.count())).isZero();

        // Audit : une entrée écrite sous clinic1 n'est jamais visible depuis clinic2 (@TenantId à l'insert + au read).
        long auditC2Avant = TenantContext.callAs(clinic2(), () -> auditLogRepository.count());
        TenantContext.runAs(clinic1(), () -> {
            AuditLog a = new AuditLog();
            a.setUsername("test");
            a.setAction("TEST_ISOLATION");
            a.setEntityType("Test");
            auditLogRepository.save(a);
        });
        assertThat(TenantContext.callAs(clinic2(), () -> auditLogRepository.count())).isEqualTo(auditC2Avant);
        assertThat(TenantContext.callAs(clinic1(), () -> auditLogRepository.count())).isGreaterThan(0);
    }

    @Test
    void config_clinique_resolue_et_distincte_par_tenant() {
        // Chaque clinique obtient SA config (P4.2) : CENTRALE = la ligne seedée (V4→V29),
        // PLATEAU = une config créée à la demande sous son tenant. Deux lignes distinctes.
        ClinicConfig c1 = TenantContext.callAs(clinic1(), () -> clinicConfigService.getConfig());
        ClinicConfig c2 = TenantContext.callAs(clinic2(), () -> clinicConfigService.getConfig());

        assertThat(c1.getClinicId()).isEqualTo(clinic1());
        assertThat(c2.getClinicId()).isEqualTo(clinic2());
        assertThat(c1.getId()).isNotEqualTo(c2.getId());
    }

    @Test
    void catalogues_cloisonnes_par_clinique() {
        // Catalogues seedés par migration SQL (departments V3, acts V4, examens imagerie V9) → CENTRALE ;
        // PLATEAU n'en a aucun (chaque clinique configure les siens). ICD-10 reste partagé (non testé ici).
        assertThat(TenantContext.callAs(clinic1(), () -> departmentRepository.count())).isGreaterThan(0);
        assertThat(TenantContext.callAs(clinic2(), () -> departmentRepository.count())).isZero();
        assertThat(TenantContext.callAs(clinic1(), () -> actCatalogRepository.count())).isGreaterThan(0);
        assertThat(TenantContext.callAs(clinic2(), () -> actCatalogRepository.count())).isZero();
        assertThat(TenantContext.callAs(clinic1(), () -> radiologyExamCatalogRepository.count())).isGreaterThan(0);
        assertThat(TenantContext.callAs(clinic2(), () -> radiologyExamCatalogRepository.count())).isZero();
    }

    @Test
    void sans_contexte_de_tenant_aucune_donnee_visible() {
        // Ni override ni utilisateur authentifié → tenant sentinelle « fermé » → rien (fail-closed).
        TenantContext.clear();
        assertThat(patientRepository.findAll()).isEmpty();
    }
}
