package com.clinic.backend.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Anti-régression templating (P1.5) : les pages clés rendent en 200 avec un vrai
 * principal (User seedé), ce qui exerce le layout + la sidebar (GlobalModelAdvice).
 * C'est exactement la classe de bug type {@code mod}→{@code navMod} qui 500-ait
 * toutes les pages — un test qui l'aurait attrapé.
 *
 * <p>P6 : depuis le cloisonnement des rôles, l'ADMIN ne voit plus le clinique ni les
 * finances. Chaque page est donc exercée avec un VRAI utilisateur seedé du bon rôle
 * (via {@code @WithUserDetails}) : médecin pour le clinique, caissier pour la caisse,
 * owner pour le pilotage, admin pour le technique.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PageRenderSmokeTest {

    @Autowired MockMvc mvc;

    /**
     * Tableau de bord médecin (vue dédiée) rendu avec un VRAI médecin seedé : ses
     * consultations/RDV/labos réels remplissent les boucles {@code th:each}, ce qui
     * exerce les expressions de ligne (dates, badges) qu'un principal mocké — dont
     * le dashboard serait vide — ne testerait pas.
     */
    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void dashboard_medecin_rend_200_avec_donnees() throws Exception {
        mvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                // en-tête de journée personnalisé (P6 WS5 c2) + table des consultations
                .andExpect(content().string(containsString("Bonjour, Dr. Martin")))
                .andExpect(content().string(containsString("Mes consultations du jour")));
    }

    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void patients_rend_200() throws Exception {
        mvc.perform(get("/patients")).andExpect(status().isOk());
    }

    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void appointments_rend_200() throws Exception {
        mvc.perform(get("/appointments")).andExpect(status().isOk());
    }

    /** Dossier patient : exerce l'agrégat coup d'œil + timeline (P3.6). */
    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void patient_detail_rend_apercu_et_timeline() throws Exception {
        mvc.perform(get("/patients/1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Chronologie du dossier")))
                // onglets deep-linkables + ARIA (P6 WS5 c2)
                .andExpect(content().string(containsString("role=\"tablist\"")))
                .andExpect(content().string(containsString("data-tab=\"lab\"")));
    }

    @Test
    @WithUserDetails(value = "caissier", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void billing_rend_200() throws Exception {
        mvc.perform(get("/billing")).andExpect(status().isOk());
    }

    /** File d'attente caisse (P5.1) : exerce le template + le filtre JS inline. */
    @Test
    @WithUserDetails(value = "caissier", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void file_attente_caisse_rend_200() throws Exception {
        mvc.perform(get("/billing/queue")).andExpect(status().isOk());
    }

    /** Détail facture : liste les paiements → exerce les libellés conviviaux
     *  ({@code @paymentMethods.label}), donc valide la résolution du bean en EL. */
    @Test
    @WithUserDetails(value = "caissier", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void facture_detail_rend_200_avec_libelles_modes() throws Exception {
        mvc.perform(get("/billing/invoices/1")).andExpect(status().isOk());
    }

    /** Cockpit de pilotage financier : réservé à l'OWNER (P6). */
    @Test
    @WithUserDetails(value = "owner", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void reports_rend_200() throws Exception {
        mvc.perform(get("/reports")).andExpect(status().isOk());
    }

    /** Config clinique : exerce la nouvelle section QR marchand (AmanaTa / MyNITA). */
    @Test
    @WithUserDetails(value = "admin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void config_rend_200_avec_section_qr_marchand() throws Exception {
        mvc.perform(get("/admin/config"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("QR marchand")));
    }

    /** Encaissement : exerce la liste de modes mise à jour + le bloc QR togglable. */
    @Test
    @WithUserDetails(value = "caissier", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void encaissement_rend_200_avec_modes_amanata_mynita() throws Exception {
        mvc.perform(get("/billing/invoices/1/pay"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("AmanaTa")))
                .andExpect(content().string(containsString("MyNITA")));
    }

    /** File des ordonnances pharmacie (P5.1 Lot C) : exerce le template + la boucle
     *  sur l'ordonnance seedée non dispensée (lignes, patient, médecin). */
    @Test
    @WithUserDetails(value = "pharmacien", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void file_ordonnances_pharmacie_rend_200() throws Exception {
        mvc.perform(get("/pharmacy/prescriptions"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ordonnances à dispenser")));
    }

    @Test
    @WithUserDetails(value = "admin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void journal_audit_rend_200() throws Exception {
        mvc.perform(get("/admin/audit")).andExpect(status().isOk());
    }
}
