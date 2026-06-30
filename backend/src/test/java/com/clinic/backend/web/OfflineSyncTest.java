package com.clinic.backend.web;

import com.clinic.backend.appointment.AppointmentRepository;
import com.clinic.backend.patient.PatientRepository;
import com.clinic.backend.repository.UserRepository;
import com.clinic.backend.tenant.ClinicRepository;
import com.clinic.backend.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B4 — rejeu serveur des RDV créés hors-ligne. L'endpoint {@code POST /appointments/offline}
 * (chaîne web, session + CSRF) doit être idempotent via {@code Idempotency-Key} : rejouer la
 * même clé NE crée PAS de doublon (critère d'acceptation), et un conflit métier renvoie 409
 * (→ le client marque l'item en échec + le conserve). Le chiffrement de la file vit côté client
 * (WebCrypto) et n'est pas testable ici ; on couvre le contrat serveur de bout en bout.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OfflineSyncTest {

    @Autowired MockMvc mvc;
    @Autowired ClinicRepository clinicRepository;
    @Autowired PatientRepository patientRepository;
    @Autowired UserRepository userRepository;
    @Autowired AppointmentRepository appointmentRepository;

    private Long centraleId() {
        return clinicRepository.findByCodeIgnoreCase("CENTRALE").orElseThrow().getId();
    }

    private Long patientId() {
        return TenantContext.callAs(centraleId(), () ->
                patientRepository.findByRecordNumberAndDeletedAtIsNull("PAT-2026-00001").orElseThrow().getId());
    }

    private Long drMartinId() {
        return userRepository.findByUsername("dr.martin").orElseThrow().getId();
    }

    private String body(Long patientId, Long doctorId, String startTime) {
        return "{\"patientId\":" + patientId + ",\"doctorId\":" + doctorId
                + ",\"startTime\":\"" + startTime + "\",\"reason\":\"RDV hors-ligne\"}";
    }

    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void rejeu_meme_cle_cree_un_seul_rdv() throws Exception {
        String key = java.util.UUID.randomUUID().toString(); // 36 car., comme crypto.randomUUID()
        String payload = body(patientId(), drMartinId(), "2030-02-11T03:30");

        // 1er rejeu : crée le RDV
        String first = mvc.perform(post("/appointments/offline").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", key)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.id").exists())
                .andReturn().getResponse().getContentAsString();

        // 2ᵉ rejeu (même clé) : idempotent → même id, pas de doublon
        mvc.perform(post("/appointments/offline").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", key)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));

        long count = TenantContext.callAs(centraleId(),
                () -> appointmentRepository.findByRequestKey(key).isPresent() ? 1L : 0L);
        assertThat(count).isEqualTo(1L);
        assertThat(first).contains("\"requestKey\":\"" + key + "\"");
    }

    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void conflit_metier_renvoie_409() throws Exception {
        // RDV dans le passé → refusé par la règle métier (non-ADMIN) → 409 (pas un 500 nu)
        String payload = body(patientId(), drMartinId(), "2000-01-01T09:00");
        mvc.perform(post("/appointments/offline").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("error"));
    }
}
