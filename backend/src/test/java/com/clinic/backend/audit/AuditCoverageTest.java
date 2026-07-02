package com.clinic.backend.audit;

import com.clinic.backend.dto.PatientDto;
import com.clinic.backend.patient.PatientService;
import com.clinic.backend.tenant.ClinicRepository;
import com.clinic.backend.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Couverture d'audit (extension) : le catalogue de filtres est toujours complet
 * (correction des menus déroulants vides), et les actions nouvellement auditées
 * (suppression patient, gestion utilisateurs) écrivent bien une trace enrichie.
 */
@SpringBootTest
@ActiveProfiles("test")
class AuditCoverageTest {

    @Autowired AuditService auditService;
    @Autowired PatientService patientService;
    @Autowired ClinicRepository clinicRepository;

    private Long centrale() {
        return clinicRepository.findByCodeIgnoreCase("CENTRALE").orElseThrow().getId();
    }

    @Test
    void catalogue_des_filtres_toujours_complet() {
        // Les menus « Type » et « Action » se peuplent depuis le catalogue connu,
        // même sans aucune trace en base (le bug d'origine : menus vides).
        assertThat(auditService.actions())
                .contains("CREATE", "UPDATE", "DELETE", "TOGGLE_ACTIVE", "PASSWORD_CHANGE",
                        "PAYMENT", "DISPENSE", "VALIDATE");
        assertThat(auditService.entityTypes())
                .contains("Patient", "User", "Invoice", "LabRequest", "Dispensation");
    }

    @Test
    void suppression_patient_est_auditee() {
        Long id = TenantContext.callAs(centrale(), () -> {
            PatientDto dto = new PatientDto();
            dto.setFirstName("Test");
            dto.setLastName("Audit");
            dto.setGender("F");
            dto.setBirthDate(LocalDate.of(1990, 1, 1));
            Long pid = patientService.create(dto).getId();
            patientService.delete(pid);
            return pid;
        });

        // La création est auditée avec la clé métier (n° de dossier) dans les détails.
        List<AuditLog> created = TenantContext.callAs(centrale(), () ->
                auditService.search(null, "Patient", "CREATE", null, null, 100));
        assertThat(created).anyMatch(e ->
                id.equals(e.getEntityId()) && e.getDetails() != null && e.getDetails().startsWith("recordNumber="));

        // La suppression de dossier est désormais tracée (n'existait pas avant).
        List<AuditLog> deleted = TenantContext.callAs(centrale(), () ->
                auditService.search(null, "Patient", "DELETE", null, null, 100));
        assertThat(deleted).anyMatch(e -> id.equals(e.getEntityId()));
    }
}
