package com.clinic.backend;

import com.clinic.backend.certificate.MedicalCertificate;
import com.clinic.backend.certificate.MedicalCertificateService;
import com.clinic.backend.dto.MedicalCertificateDto;
import com.clinic.backend.tenant.ClinicRepository;
import com.clinic.backend.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tier E1 — certificats médicaux. Numérotation, calcul du repos, nettoyage des dates pour un
 * type non-repos, pré-remplissage depuis une consultation, génération PDF, et gating MEDECIN.
 * Patron tenant : tenant figé CENTRALE en {@code @BeforeTransaction} ; médecin via {@code @WithUserDetails}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CertificateTest {

    private static final String UDS = "userDetailsServiceImpl";
    private static final long P1 = 1L;  // patient seedé (CENTRALE)
    private static final long C1 = 1L;  // consultation seedée (CENTRALE)

    @Autowired MedicalCertificateService certificateService;
    @Autowired ClinicRepository clinicRepository;
    @Autowired MockMvc mvc;

    @BeforeTransaction
    void setTenant() {
        TenantContext.set(clinicRepository.findByCodeIgnoreCase("CENTRALE").orElseThrow().getId());
    }

    @AfterTransaction
    void clearTenant() {
        TenantContext.clear();
    }

    private MedicalCertificateDto dto(String type) {
        MedicalCertificateDto d = new MedicalCertificateDto();
        d.setPatientId(P1);
        d.setType(type);
        return d;
    }

    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = UDS)
    void cree_un_certificat_numerote_avec_repos_calcule() {
        MedicalCertificateDto d = dto("ARRET_TRAVAIL");
        d.setRestStartDate(LocalDate.now());
        d.setRestEndDate(LocalDate.now().plusDays(4)); // 5 jours (bornes inclusives)
        MedicalCertificate saved = certificateService.create(d);

        assertThat(saved.getCertificateNumber()).startsWith("CERT-" + LocalDate.now().getYear() + "-");
        assertThat(saved.getDoctor().getUsername()).isEqualTo("dr.martin"); // émetteur = user courant
        assertThat(saved.getType()).isEqualTo("ARRET_TRAVAIL");
        assertThat(saved.getRestDays()).isEqualTo(5);

        // Type non-repos → les dates de repos sont ignorées/effacées
        MedicalCertificateDto g = dto("GENERAL");
        g.setRestStartDate(LocalDate.now());
        g.setRestEndDate(LocalDate.now().plusDays(4));
        MedicalCertificate general = certificateService.create(g);
        assertThat(general.getRestDays()).isNull();
        assertThat(general.getRestStartDate()).isNull();
    }

    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = UDS)
    void prefill_depuis_consultation_remplit_le_patient() {
        MedicalCertificateDto d = certificateService.prefill(C1, null);
        assertThat(d.getConsultationId()).isEqualTo(C1);
        assertThat(d.getPatientId()).isNotNull();
        assertThat(d.getType()).isEqualTo("GENERAL");
    }

    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = UDS)
    void pdf_genere_un_binaire() throws Exception {
        MedicalCertificate saved = certificateService.create(dto("BONNE_SANTE"));
        mvc.perform(get("/certificates/" + saved.getId() + "/pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("application/pdf")));
    }

    @Test
    @WithMockUser(username = "doc", roles = "MEDECIN")
    void medecin_voit_la_liste() throws Exception {
        mvc.perform(get("/certificates")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "sec", roles = "SECRETAIRE")
    void secretaire_refuse() throws Exception {
        mvc.perform(get("/certificates")).andExpect(status().isForbidden());
    }
}
