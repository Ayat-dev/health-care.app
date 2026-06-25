package com.clinic.backend.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fil d'Ariane partagé (P6 WS5) : auto-dérivé dans {@code layouts/base.html} à partir de
 * {@code currentUri} + {@code currentModule}. Présent sur une sous-vue (Accueil / Module / Page),
 * absent sur la page d'accueil du rôle (on y est déjà).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BreadcrumbNavigationTest {

    @Autowired MockMvc mvc;

    @Test
    @WithMockUser(username = "cash", roles = "CAISSIER")
    void le_fil_d_ariane_apparait_sur_une_sous_vue() throws Exception {
        // Le caissier ouvre le rapport financier (sous-vue du module Rapports) → fil d'Ariane
        // avec le lien Accueil et le segment de la page courante.
        mvc.perform(get("/reports/financial"))
           .andExpect(status().isOk())
           .andExpect(content().string(allOf(
                   containsString("class=\"breadcrumb\""),
                   containsString("Accueil"),
                   containsString("breadcrumb-current"))));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void pas_de_fil_d_ariane_sur_la_page_d_accueil() throws Exception {
        // /admin/users est la home de l'ADMIN → pas de fil d'Ariane (cul-de-sac évité, rien à remonter).
        mvc.perform(get("/admin/users"))
           .andExpect(status().isOk())
           .andExpect(content().string(not(containsString("class=\"breadcrumb\""))));
    }

    // ── Sous-navigation de module (P6 WS5) — onglets persistants ─────────────

    @Test
    @WithMockUser(username = "pharma", roles = "PHARMACIEN")
    void la_sous_nav_pharmacie_marque_l_onglet_actif_le_plus_specifique() throws Exception {
        // Sur /pharmacy/stock, l'onglet Stock est actif (préfixe le plus long), pas Tableau de bord.
        mvc.perform(get("/pharmacy/stock"))
           .andExpect(status().isOk())
           .andExpect(content().string(allOf(
                   containsString("class=\"module-tabs\""),
                   containsString("module-tab active"),
                   containsString("href=\"/pharmacy/dispensations\""))));
    }

    @Test
    @WithMockUser(username = "doc", roles = "MEDECIN")
    void pas_de_sous_nav_sur_un_module_sans_onglets() throws Exception {
        // Le module Consultations n'a pas de registre d'onglets → pas de barre module-tabs.
        mvc.perform(get("/consultations"))
           .andExpect(status().isOk())
           .andExpect(content().string(not(containsString("class=\"module-tabs\""))));
    }
}
