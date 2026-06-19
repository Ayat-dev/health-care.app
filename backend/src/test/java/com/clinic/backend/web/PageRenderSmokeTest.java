package com.clinic.backend.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Anti-régression templating (P1.5) : les pages clés rendent en 200 avec un vrai
 * principal (User seedé), ce qui exerce le layout + la sidebar (GlobalModelAdvice).
 * C'est exactement la classe de bug type {@code mod}→{@code navMod} qui 500-ait
 * toutes les pages — un test qui l'aurait attrapé.
 *
 * {@code @WithUserDetails} charge l'admin seedé via le bean UserDetailsService,
 * donc le principal est un {@code com.clinic.backend.model.User} (pas le User
 * générique de Spring) — le badge notifications et RoleProfile fonctionnent.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithUserDetails(value = "admin", userDetailsServiceBeanName = "userDetailsServiceImpl")
class PageRenderSmokeTest {

    @Autowired MockMvc mvc;

    @Test
    void dashboard_rend_200() throws Exception {
        mvc.perform(get("/dashboard")).andExpect(status().isOk());
    }

    @Test
    void patients_rend_200() throws Exception {
        mvc.perform(get("/patients")).andExpect(status().isOk());
    }

    @Test
    void appointments_rend_200() throws Exception {
        mvc.perform(get("/appointments")).andExpect(status().isOk());
    }

    @Test
    void billing_rend_200() throws Exception {
        mvc.perform(get("/billing")).andExpect(status().isOk());
    }

    @Test
    void reports_rend_200() throws Exception {
        mvc.perform(get("/reports")).andExpect(status().isOk());
    }

    @Test
    void journal_audit_rend_200() throws Exception {
        mvc.perform(get("/admin/audit")).andExpect(status().isOk());
    }
}
