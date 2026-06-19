package com.clinic.backend.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Portail patient (P2.4) : le compte seedé {@code patient} (rôle PATIENT, rattaché au
 * dossier de p1) accède à son espace en lecture et peut soumettre une demande de RDV.
 *
 * {@code @WithUserDetails} charge le User réel via le bean UserDetailsService, donc
 * {@link com.clinic.backend.portal.PortalService} résout bien le dossier patient lié.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithUserDetails(value = "patient", userDetailsServiceBeanName = "userDetailsServiceImpl")
class PortalTest {

    @Autowired MockMvc mvc;

    @Test
    void accueil_rend_200() throws Exception {
        mvc.perform(get("/portal")).andExpect(status().isOk());
    }

    @Test
    void mes_rendezvous_rend_200() throws Exception {
        mvc.perform(get("/portal/appointments")).andExpect(status().isOk());
    }

    @Test
    void mon_dossier_rend_200() throws Exception {
        mvc.perform(get("/portal/record")).andExpect(status().isOk());
    }

    @Test
    void formulaire_demande_rdv_rend_200() throws Exception {
        mvc.perform(get("/portal/appointments/request")).andExpect(status().isOk());
    }

    @Test
    void demande_rdv_sans_medecin_reaffiche_le_formulaire() throws Exception {
        // patientId forcé côté serveur ; médecin manquant → erreur métier → formulaire re-rendu (200).
        mvc.perform(post("/portal/appointments/request").with(csrf())
                        .param("startTime", "2030-01-01T09:00"))
           .andExpect(status().isOk());
    }
}
