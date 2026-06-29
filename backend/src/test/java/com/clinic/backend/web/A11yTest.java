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
 * Accessibilité WCAG 2.2 (P3.4) : le chrome partagé expose les repères et
 * attributs ARIA attendus — lien d'évitement, landmark principal, état actif.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class A11yTest {

    @Autowired MockMvc mvc;

    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void chrome_expose_lien_evitement_et_landmark() throws Exception {
        mvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                // 2.4.1 Bypass Blocks
                .andExpect(content().string(containsString("class=\"skip-link\"")))
                .andExpect(content().string(containsString("href=\"#main-content\"")))
                // landmark principal ciblé par le lien d'évitement
                .andExpect(content().string(containsString("id=\"main-content\"")))
                // 4.1.2 — navigation nommée
                .andExpect(content().string(containsString("Navigation principale")));
    }

    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void nav_actif_porte_aria_current() throws Exception {
        // /dashboard est actif → l'item correspondant porte aria-current="page"
        mvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("aria-current=\"page\"")));
    }

    @Test
    void login_erreur_est_annoncee_role_alert() throws Exception {
        mvc.perform(get("/login").param("error", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("role=\"alert\"")));
    }

    // ── C1 : audit a11y des vues à fort trafic (labels, scope) ──────────────

    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void formulaire_patient_associe_label_et_champ() throws Exception {
        // 1.3.1 / 3.3.2 — chaque champ a un <label for> relié à son id.
        mvc.perform(get("/patients/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("for=\"firstName\"")))
                .andExpect(content().string(containsString("id=\"firstName\"")))
                .andExpect(content().string(containsString("for=\"lastName\"")));
    }

    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void tableau_patients_porte_scope_col() throws Exception {
        // 1.3.1 — en-têtes de colonne explicitement portés (scope="col").
        mvc.perform(get("/patients"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("scope=\"col\"")));
    }

    // ── C2 : audit a11y des modules restants (labo, etc.) ───────────────────

    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void formulaire_labo_associe_label_et_champ() throws Exception {
        mvc.perform(get("/lab/requests/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("for=\"patientId\"")))
                .andExpect(content().string(containsString("id=\"patientId\"")));
    }

    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void worklist_labo_porte_scope_col() throws Exception {
        mvc.perform(get("/lab"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("scope=\"col\"")));
    }
}
