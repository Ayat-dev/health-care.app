package com.clinic.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Matrice RBAC (P1.5) : codifie ce qui était vérifié à la main — pour chaque rôle,
 * un échantillon d'endpoints attendus en 200 / 403, + le comportement non authentifié.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityMatrixTest {

    @Autowired MockMvc mvc;

    // ── Non authentifié ──────────────────────────────────────────────────────

    @Test
    void api_sans_token_est_refusee() throws Exception {
        mvc.perform(get("/api/patients"))
           .andExpect(status().is4xxClientError()); // 401/403 selon l'entry point
    }

    @Test
    void page_web_sans_session_redirige_vers_login() throws Exception {
        mvc.perform(get("/dashboard"))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrlPattern("**/login"));
    }

    // ── /admin/audit : ADMIN uniquement ──────────────────────────────────────

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void admin_accede_au_journal_audit() throws Exception {
        mvc.perform(get("/admin/audit")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "doc", roles = "MEDECIN")
    void medecin_refuse_sur_journal_audit() throws Exception {
        mvc.perform(get("/admin/audit")).andExpect(status().isForbidden());
    }

    // ── /reports/dashboard : ADMIN + MEDECIN ─────────────────────────────────

    @Test
    @WithMockUser(username = "doc", roles = "MEDECIN")
    void medecin_accede_au_dashboard_rapports() throws Exception {
        mvc.perform(get("/reports/dashboard")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "cash", roles = "CAISSIER")
    void caissier_refuse_sur_dashboard_rapports() throws Exception {
        mvc.perform(get("/reports/dashboard")).andExpect(status().isForbidden());
    }

    // ── /reports/financial : ADMIN + CAISSIER ────────────────────────────────

    @Test
    @WithMockUser(username = "cash", roles = "CAISSIER")
    void caissier_accede_au_rapport_financier() throws Exception {
        mvc.perform(get("/reports/financial")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "doc", roles = "MEDECIN")
    void medecin_refuse_sur_rapport_financier() throws Exception {
        mvc.perform(get("/reports/financial")).andExpect(status().isForbidden());
    }

    // ── /portal : PATIENT uniquement ─────────────────────────────────────────

    @Test
    @WithMockUser(username = "doc", roles = "MEDECIN")
    void medecin_refuse_sur_portail() throws Exception {
        mvc.perform(get("/portal")).andExpect(status().isForbidden());
    }

    // ── La page de login reste publique ──────────────────────────────────────

    @Test
    void login_est_public() throws Exception {
        mvc.perform(get("/login"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("ClinicApp")));
    }

    // ── PWA (P3.1) : ressources installables accessibles sans session ────────

    @Test
    void manifest_pwa_est_public() throws Exception {
        mvc.perform(get("/manifest.webmanifest"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("\"short_name\"")));
    }

    @Test
    void service_worker_est_public() throws Exception {
        mvc.perform(get("/sw.js"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("CACHE_VERSION")));
    }

    @Test
    void page_offline_est_publique() throws Exception {
        mvc.perform(get("/offline.html"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("Connexion indisponible")));
    }
}
