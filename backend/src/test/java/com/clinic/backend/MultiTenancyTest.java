package com.clinic.backend;

import com.clinic.backend.patient.Patient;
import com.clinic.backend.patient.PatientRepository;
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
    void sans_contexte_de_tenant_aucune_donnee_visible() {
        // Ni override ni utilisateur authentifié → tenant sentinelle « fermé » → rien (fail-closed).
        TenantContext.clear();
        assertThat(patientRepository.findAll()).isEmpty();
    }
}
