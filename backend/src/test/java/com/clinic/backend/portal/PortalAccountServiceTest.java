package com.clinic.backend.portal;

import com.clinic.backend.dto.PatientDto;
import com.clinic.backend.model.User;
import com.clinic.backend.patient.PatientService;
import com.clinic.backend.repository.UserRepository;
import com.clinic.backend.tenant.ClinicRepository;
import com.clinic.backend.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Onboarding du compte portail : activation crée + lie un compte PATIENT avec un
 * mot de passe temporaire ; double activation rejetée ; reset régénère le mot de passe.
 */
@SpringBootTest
@ActiveProfiles("test")
class PortalAccountServiceTest {

    @Autowired PortalAccountService portalAccountService;
    @Autowired PatientService patientService;
    @Autowired UserRepository userRepository;
    @Autowired ClinicRepository clinicRepository;

    private Long centrale() {
        return clinicRepository.findByCodeIgnoreCase("CENTRALE").orElseThrow().getId();
    }

    private Long newPatientId() {
        PatientDto dto = new PatientDto();
        dto.setFirstName("Portail");
        dto.setLastName("Test");
        dto.setGender("F");
        dto.setBirthDate(LocalDate.of(1995, 5, 5));
        return patientService.create(dto).getId();
    }

    @Test
    void activation_cree_un_compte_patient_lie_avec_mot_de_passe_temporaire() {
        TenantContext.runAs(centrale(), () -> {
            Long pid = newPatientId();

            assertThat(portalAccountService.status(pid).activated()).isFalse();

            var creds = portalAccountService.activate(pid);
            assertThat(creds.username()).isNotBlank();
            assertThat(creds.tempPassword()).hasSize(8);

            User u = userRepository.findByUsername(creds.username()).orElseThrow();
            assertThat(u.getRole()).isEqualTo("PATIENT");
            assertThat(u.isActive()).isTrue();

            PortalAccountService.Status st = portalAccountService.status(pid);
            assertThat(st.activated()).isTrue();
            assertThat(st.username()).isEqualTo(creds.username());

            // Double activation refusée
            assertThatThrownBy(() -> portalAccountService.activate(pid))
                    .isInstanceOf(IllegalStateException.class);

            // Reset régénère un mot de passe (identifiant inchangé)
            var reset = portalAccountService.resetPassword(pid);
            assertThat(reset.username()).isEqualTo(creds.username());
            assertThat(reset.tempPassword()).isNotEqualTo(creds.tempPassword());
        });
    }
}
