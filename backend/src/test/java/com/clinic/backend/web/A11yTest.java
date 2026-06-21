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
    @WithUserDetails(value = "admin", userDetailsServiceBeanName = "userDetailsServiceImpl")
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
    @WithUserDetails(value = "admin", userDetailsServiceBeanName = "userDetailsServiceImpl")
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
}
