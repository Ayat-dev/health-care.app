package com.clinic.backend.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exports (chantiers A & B) : les endpoints Excel/PDF renvoient un binaire en
 * pièce jointe pour le bon rôle, et respectent le cloisonnement (registre patients
 * = PHI, refusé au OWNER).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FicheExportWebTest {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired MockMvc mvc;

    // ── Chantier B : fiches opérationnelles ──────────────────────────────────

    @Test
    @WithUserDetails(value = "caissier", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void export_factures_xlsx() throws Exception {
        mvc.perform(get("/billing/invoices/export"))
           .andExpect(status().isOk())
           .andExpect(header().string("Content-Type", XLSX))
           .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("factures.xlsx")));
    }

    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void export_registre_patients_xlsx() throws Exception {
        mvc.perform(get("/patients/export"))
           .andExpect(status().isOk())
           .andExpect(header().string("Content-Type", XLSX));
    }

    @Test
    @WithUserDetails(value = "owner", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void owner_refuse_sur_registre_patients() throws Exception {
        // Le registre patients est du PHI : le propriétaire (business) n'y a pas accès.
        mvc.perform(get("/patients/export")).andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void export_rendez_vous_xlsx() throws Exception {
        mvc.perform(get("/appointments/export"))
           .andExpect(status().isOk())
           .andExpect(header().string("Content-Type", XLSX));
    }

    @Test
    @WithUserDetails(value = "pharmacien", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void export_dispensations_xlsx() throws Exception {
        mvc.perform(get("/pharmacy/dispensations/export"))
           .andExpect(status().isOk())
           .andExpect(header().string("Content-Type", XLSX));
    }

    // ── Aperçu + sélection de colonnes ───────────────────────────────────────

    @Test
    @WithUserDetails(value = "caissier", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void apercu_factures_rend_200_avec_colonnes() throws Exception {
        mvc.perform(get("/billing/invoices/export/preview"))
           .andExpect(status().isOk())
           .andExpect(content().string(org.hamcrest.Matchers.containsString("N° facture")))
           .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"cols\"")));
    }

    @Test
    @WithUserDetails(value = "caissier", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void export_factures_colonnes_filtrees_reste_xlsx() throws Exception {
        mvc.perform(get("/billing/invoices/export").param("cols", "number").param("cols", "patient"))
           .andExpect(status().isOk())
           .andExpect(header().string("Content-Type", XLSX));
    }

    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void apercu_registre_patients_rend_200() throws Exception {
        mvc.perform(get("/patients/export/preview"))
           .andExpect(status().isOk())
           .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"cols\"")));
    }

    // ── Chantier A : exports rapports nouvellement câblés ────────────────────

    @Test
    @WithUserDetails(value = "owner", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void export_bilan_financier_excel() throws Exception {
        mvc.perform(get("/reports/financial/excel"))
           .andExpect(status().isOk())
           .andExpect(header().string("Content-Type", XLSX));
    }

    @Test
    @WithUserDetails(value = "owner", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void apercu_rapport_activite_liste_les_sections() throws Exception {
        mvc.perform(get("/reports/activity/export/preview"))
           .andExpect(status().isOk())
           .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"sections\"")))
           .andExpect(content().string(org.hamcrest.Matchers.containsString("Consultations par département")));
    }

    @Test
    @WithUserDetails(value = "owner", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void export_rapport_sections_filtrees_reste_xlsx() throws Exception {
        mvc.perform(get("/reports/activity/excel").param("sections", "kpis"))
           .andExpect(status().isOk())
           .andExpect(header().string("Content-Type", XLSX));
    }

    @Test
    @WithUserDetails(value = "owner", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void export_impayes_pdf() throws Exception {
        mvc.perform(get("/reports/outstanding/pdf"))
           .andExpect(status().isOk())
           .andExpect(content_type_pdf());
    }

    private static org.springframework.test.web.servlet.ResultMatcher content_type_pdf() {
        return header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE);
    }
}
