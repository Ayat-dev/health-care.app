package com.clinic.backend.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

    // ── D4b : profil ─────────────────────────────────────────────────────────
    @Test
    void profil_rend_200() throws Exception {
        mvc.perform(get("/portal/profile")).andExpect(status().isOk());
    }

    @Test
    void change_mot_de_passe_puis_restaure() throws Exception {
        // Succès → 302. On restaure le mot de passe d'origine pour ne pas polluer la suite.
        mvc.perform(post("/portal/profile/password").with(csrf())
                        .param("currentPassword", "patient123")
                        .param("newPassword", "nouveau123")
                        .param("confirmPassword", "nouveau123"))
           .andExpect(status().is3xxRedirection());
        mvc.perform(post("/portal/profile/password").with(csrf())
                        .param("currentPassword", "nouveau123")
                        .param("newPassword", "patient123")
                        .param("confirmPassword", "patient123"))
           .andExpect(status().is3xxRedirection());
    }

    @Test
    void change_mot_de_passe_confirmation_differente_redirige() throws Exception {
        // La confirmation ne correspond pas → redirect avec flash erreur, aucune mutation.
        mvc.perform(post("/portal/profile/password").with(csrf())
                        .param("currentPassword", "patient123")
                        .param("newPassword", "nouveau123")
                        .param("confirmPassword", "autre9999"))
           .andExpect(status().is3xxRedirection());
    }

    // ── D4b : téléchargements PDF (cloisonnés) ──────────────────────────────────
    @Test
    void telecharge_son_bulletin_labo_pdf() throws Exception {
        assertPdf("/portal/lab/1/pdf"); // LAB req 1 = p1, VALIDE
    }

    @Test
    void telecharge_son_ordonnance_pdf() throws Exception {
        assertPdf("/portal/prescriptions/1/pdf"); // ORD-…-00001 de la consultation de p1
    }

    @Test
    void telecharge_son_recu_pdf() throws Exception {
        assertPdf("/portal/invoices/1/receipt/pdf"); // FAC-…-00001 = p1
    }

    @Test
    void telecharge_son_certificat_pdf() throws Exception {
        assertPdf("/portal/certificates/1/pdf"); // CERT-…-00001 = p1 (E1-bis)
    }

    @Test
    void telechargement_document_autrui_refuse() throws Exception {
        // LAB req 2 appartient à p2 → 403 (cloisonnement).
        mvc.perform(get("/portal/lab/2/pdf")).andExpect(status().isForbidden());
    }

    @Test
    void telechargement_certificat_autrui_refuse() throws Exception {
        // CERT-…-00002 appartient à p2 → 403 (cloisonnement).
        mvc.perform(get("/portal/certificates/2/pdf")).andExpect(status().isForbidden());
    }

    // ── D4b : annulation de RDV (cloisonnée) ────────────────────────────────────
    @Test
    void annule_son_rdv() throws Exception {
        // RDV 4 = p1, PLANIFIE → annulable.
        mvc.perform(post("/portal/appointments/4/cancel").with(csrf()))
           .andExpect(status().is3xxRedirection());
    }

    @Test
    void annule_rdv_autrui_refuse() throws Exception {
        // RDV 2 = p2 → 403.
        mvc.perform(post("/portal/appointments/2/cancel").with(csrf()))
           .andExpect(status().isForbidden());
    }

    private void assertPdf(String url) throws Exception {
        MvcResult res = mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn();
        byte[] body = res.getResponse().getContentAsByteArray();
        assertThat(body.length).isGreaterThan(500);
        assertThat(new String(body, 0, 5)).isEqualTo("%PDF-");
    }
}
