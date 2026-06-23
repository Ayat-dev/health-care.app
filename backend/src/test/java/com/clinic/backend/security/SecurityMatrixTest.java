package com.clinic.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
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

    // ── Hub /reports : ADMIN + MEDECIN voient la direction ; les autres rôles ──
    // porteurs du module REPORTS sont redirigés vers le rapport qu'ils peuvent ouvrir.

    @Test
    @WithMockUser(username = "doc", roles = "MEDECIN")
    void medecin_accede_au_dashboard_rapports() throws Exception {
        mvc.perform(get("/reports/dashboard")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "cash", roles = "CAISSIER")
    void caissier_redirige_du_hub_rapports_vers_financier() throws Exception {
        // Avant : 403 (le lien « Rapports » de la sidebar menait à une impasse).
        // Désormais : redirigé vers le rapport financier qu'il a le droit de consulter.
        mvc.perform(get("/reports/dashboard"))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/reports/financial"));
    }

    @Test
    @WithMockUser(username = "sec", roles = "SECRETAIRE")
    void secretaire_redirige_du_hub_rapports_vers_impayes() throws Exception {
        mvc.perform(get("/reports/dashboard"))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/reports/outstanding"));
    }

    // ── /dashboard : KPI réservé ADMIN ; MEDECIN a sa vue dédiée ; autres redirigés ──

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void admin_voit_le_tableau_de_bord_kpi() throws Exception {
        mvc.perform(get("/dashboard")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "doc", roles = "MEDECIN")
    void medecin_voit_son_tableau_de_bord_dedie() throws Exception {
        mvc.perform(get("/dashboard")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "cash", roles = "CAISSIER")
    void caissier_sur_dashboard_redirige_vers_son_accueil() throws Exception {
        // Pas de tableau de bord KPI pour le caissier → renvoyé vers sa page métier (facturation).
        mvc.perform(get("/dashboard"))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/billing"));
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

    // ── /admin/clinics : SUPER_ADMIN uniquement (registre des tenants, P4.2) ──

    @Test
    @WithMockUser(username = "root", roles = "SUPER_ADMIN")
    void super_admin_accede_au_registre_des_cliniques() throws Exception {
        mvc.perform(get("/admin/clinics"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("CENTRALE")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void admin_clinique_refuse_sur_registre_des_cliniques() throws Exception {
        mvc.perform(get("/admin/clinics")).andExpect(status().isForbidden());
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

    // ── Actuator (P4.3) : health/info publics, prometheus/metrics protégés ────

    @Test
    void actuator_health_est_public() throws Exception {
        mvc.perform(get("/actuator/health"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("UP")));
    }

    @Test
    void actuator_prometheus_exige_authentification() throws Exception {
        mvc.perform(get("/actuator/prometheus"))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void actuator_metrics_exige_authentification() throws Exception {
        mvc.perform(get("/actuator/metrics"))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void actuator_accessible_avec_le_compte_de_scraping() throws Exception {
        // Prouve le chemin d'auth du scraper de bout en bout (HTTP Basic →
        // ENDPOINT_ADMIN → endpoint actuator réel). On vise /actuator/metrics
        // (endpoint core, mappé en MockMvc) ; /actuator/prometheus se comporte
        // pareil côté sécurité mais n'est pas mappé sous MockMvc (vérifié à la
        // main en dev : 200 + métriques jvm_ au format Prometheus).
        mvc.perform(get("/actuator/metrics").with(httpBasic("monitor", "clinicapp-test-monitoring-secret")))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("names")));
    }
}
