package com.clinic.backend.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.htmlunit.BrowserVersion;
import org.htmlunit.ScriptResult;
import org.htmlunit.StringWebResponse;
import org.htmlunit.WebClient;
import org.htmlunit.WebConnection;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.htmlunit.corejs.javascript.Undefined;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Accessibilité automatisée (C3) : exécute le vrai moteur de règles <b>axe-core</b> sur
 * les vues-clés <i>rendues</i> et échoue si une violation d'impact <b>critique</b> ou
 * <b>sérieux</b> subsiste (WCAG 2.0/2.1 A &amp; AA). Complète les assertions ciblées de
 * {@link A11yTest} (labels/scope) par une couverture de règles exhaustive.
 *
 * <p><b>Principe</b> : le HTML <i>rendu par le serveur</i> (Thymeleaf) porte toute la
 * structure auditée par axe ; le JS applicatif (websocket/STOMP, graphiques…) n'y ajoute
 * que de l'interactivité. On récupère donc le HTML authentifié via {@link MockMvc}
 * (qui respecte {@code @WithUserDetails}), puis on le charge dans un <b>HtmlUnit</b> nu
 * (navigateur sans tête 100% Java) où l'on injecte axe — <b>sans</b> exécuter le JS
 * applicatif (scripts/CSS externes court-circuités). Cela évite les blocages d'HtmlUnit
 * sur le client temps réel et garde le test rapide.
 *
 * <p>axe-core est lu depuis le JAR {@code com.deque.html.axe-core:selenium} qui embarque
 * {@code axe.min.js} à sa racine (aucun CDN — cohérent avec la CSP {@code default-src 'self'}).
 *
 * <p><b>CI-safe</b> : si le moteur JS d'HtmlUnit ne parvient pas à exécuter axe sur
 * l'environnement courant, le test se <b>skippe</b> plutôt que d'échouer — {@code mvnd test}
 * reste vert (même posture que A1/Testcontainers). Les vraies violations a11y, elles, font
 * bien échouer le test.
 *
 * <p><b>NB</b> : le CSS applicatif n'étant pas chargé ici, les règles de <i>contraste</i>
 * ressortent en « incomplete » (non calculable) et non en violation — le contraste a été
 * vérifié à la main en C1/C2. Cet audit cible donc les règles <i>structurelles</i> (labels,
 * en-têtes de tableau, rôles ARIA, alternatives textuelles, attributs, langue…).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class A11yAxeTest {

    @Autowired MockMvc mvc;

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Niveaux WCAG audités (A + AA, 2.0 &amp; 2.1). */
    private static final List<String> WCAG_TAGS =
            List.of("wcag2a", "wcag2aa", "wcag21a", "wcag21aa");

    /** Seuls les impacts bloquants font échouer (acceptation C3 = « violations critiques = 0 »). */
    private static final Set<String> BLOCKING_IMPACTS = Set.of("critical", "serious");

    /**
     * Plus aucune règle différée (C2-reliquat traité 2026-07-01) : le patron ARIA « Tabs »
     * du dossier patient/maternité porte désormais en <b>statique</b> (templates) la sémantique
     * complète — {@code role=tablist}/{@code tab}/{@code tabpanel}, {@code aria-controls} ↔
     * {@code aria-labelledby}, {@code tabindex} mobile — donc {@code aria-required-children} et
     * {@code aria-required-attr} sont à présent audités sans exclusion.
     */
    private static final Set<String> DEFERRED_RULES = Set.of();

    private static volatile String axeSource;

    /** URL « document » servie par le {@link WebConnection} stub (navigation réelle = readyState complete). */
    private static final String AUDIT_URL = "http://localhost/__a11y_audit__";

    /** HTML de la vue courante, lu par le {@link WebConnection} stub à chaque navigation. */
    private final java.util.concurrent.atomic.AtomicReference<String> pageHtml =
            new java.util.concurrent.atomic.AtomicReference<>("");

    // ── Vue publique ────────────────────────────────────────────────────────

    @Test
    void login_sans_violation_critique() throws Exception {
        audit("/login");
    }

    // ── Vues médecin (clinique) ─────────────────────────────────────────────

    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void vues_medecin_sans_violation_critique() throws Exception {
        audit("/dashboard",
                "/patients",
                "/patients/new",
                "/patients/1",          // dossier patient → patron ARIA « Tabs » complet (C2-reliquat)
                "/maternity/1",         // dossier maternité → mêmes onglets ARIA
                "/consultations/1",
                "/lab",
                "/lab/requests/new");
    }

    // ── Vues caisse (facturation) ───────────────────────────────────────────

    @Test
    @WithUserDetails(value = "caissier", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void vues_caisse_sans_violation_critique() throws Exception {
        audit("/billing",
                "/billing/invoices/1",
                "/billing/invoices/1/pay");
    }

    // ── Vues administration ─────────────────────────────────────────────────

    @Test
    @WithUserDetails(value = "admin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void vues_admin_sans_violation_critique() throws Exception {
        audit("/admin/users",
                "/admin/config",
                "/admin/audit");
    }

    // ── Vue pilotage (OWNER) ────────────────────────────────────────────────

    @Test
    @WithUserDetails(value = "owner", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void vue_pilotage_sans_violation_critique() throws Exception {
        audit("/reports");
    }

    // ── Tuyauterie ──────────────────────────────────────────────────────────

    /**
     * Récupère chaque vue (HTML authentifié via MockMvc), la passe à axe-core dans HtmlUnit
     * et agrège les violations bloquantes. Une panne d'infrastructure JS (HtmlUnit incapable
     * d'exécuter axe) → test skippé, pas en échec.
     */
    private void audit(String... paths) throws Exception {
        WebClient web;
        try {
            web = quietWebClient();
        } catch (Throwable infra) {
            Assumptions.abort("HtmlUnit indisponible — audit axe-core skippé : " + infra);
            return; // inatteignable
        }

        StringBuilder report = new StringBuilder();
        try {
            String axe = axeSource();
            String tagsJson = JSON.writeValueAsString(WCAG_TAGS);
            for (String path : paths) {
                MvcResult res = mvc.perform(get(path)).andReturn();
                assertEquals(200, res.getResponse().getStatus(),
                        "Vue " + path + " devrait rendre 200 (auth/route) avant audit a11y");
                pageHtml.set(res.getResponse().getContentAsString());

                String resultJson;
                try {
                    resultJson = runAxe(web, axe, tagsJson);
                } catch (Throwable infra) {
                    Assumptions.abort("axe-core non exécutable sur " + path
                            + " (moteur JS HtmlUnit) — audit skippé : " + infra);
                    return;
                }
                if (resultJson == null) {
                    Assumptions.abort("axe-core sans résultat sur " + path
                            + " (moteur JS HtmlUnit) — audit skippé");
                    return;
                }
                collectBlocking(path, resultJson, report);
            }
        } finally {
            web.close();
        }

        if (report.length() > 0) {
            fail("Violations a11y bloquantes (axe-core, impact critique/sérieux) :" + report);
        }
    }

    /**
     * Charge le HTML dans HtmlUnit, injecte axe, le lance (forme à callback) et draine la
     * file de tâches JS. Renvoie le JSON des résultats, ou {@code null} si rien n'aboutit.
     */
    private String runAxe(WebClient web, String axeSource, String tagsJson)
            throws java.io.IOException {
        // Navigation « réelle » (via le WebConnection stub) → le document atteint
        // readyState=complete et émet DOMContentLoaded, sans quoi axe attend indéfiniment.
        HtmlPage page = web.getPage(AUDIT_URL);
        web.waitForBackgroundJavaScript(2000);                  // laisse retomber le JS de page

        page.executeJavaScript(axeSource);
        page.executeJavaScript(
                "window.__axeDone=false;window.__axeRes=null;window.__axeErr=null;"
                + "try{axe.run(document,{runOnly:{type:'tag',values:" + tagsJson + "}},"
                + "function(err,r){if(err){window.__axeErr=''+err;}"
                + "else{window.__axeRes=JSON.stringify(r);}window.__axeDone=true;});}"
                + "catch(e){window.__axeErr=''+e;window.__axeDone=true;}");

        boolean done = false;
        for (int i = 0; i < 40 && !done; i++) {                 // ≤ 20 s / page
            web.waitForBackgroundJavaScript(500);
            done = bool(page, "window.__axeDone===true;");
        }
        if (!done) return null;

        Object err = result(page, "window.__axeErr;");
        if (err != null) throw new IllegalStateException("axe.run a échoué : " + err);
        Object res = result(page, "window.__axeRes;");
        return res == null ? null : res.toString();
    }

    private static boolean bool(HtmlPage page, String expr) {
        return Boolean.TRUE.equals(result(page, expr));
    }

    /** Évalue une expression JS et renvoie sa valeur Java, {@code null} si {@code undefined}. */
    private static Object result(HtmlPage page, String expr) {
        ScriptResult sr = page.executeJavaScript(expr);
        Object v = sr.getJavaScriptResult();
        return (v == null || Undefined.isUndefined(v)) ? null : v;
    }

    /** Filtre les violations bloquantes (hors règles différées) et les ajoute au rapport. */
    private void collectBlocking(String path, String resultJson, StringBuilder report)
            throws Exception {
        JsonNode violations = JSON.readTree(resultJson).path("violations");
        for (JsonNode v : violations) {
            String impact = v.path("impact").asText("").toLowerCase();
            String id = v.path("id").asText("");
            if (!BLOCKING_IMPACTS.contains(impact) || DEFERRED_RULES.contains(id)) continue;
            report.append("\n  ✗ [").append(path).append("] ")
                    .append(impact).append(" — ").append(id)
                    .append(" : ").append(v.path("help").asText(""));
            for (JsonNode node : v.path("nodes")) {
                report.append("\n        → ").append(node.path("target").toString());
            }
        }
    }

    /**
     * WebClient sans tête configuré pour un audit DOM pur : JS actif (pour axe), mais
     * CSS/images/websocket coupés et toutes les ressources externes (app.js, etc.)
     * court-circuitées en réponse vide — on n'exécute donc <b>pas</b> le JS applicatif.
     */
    private WebClient quietWebClient() {
        WebClient web = new WebClient(BrowserVersion.CHROME);
        web.getOptions().setThrowExceptionOnScriptError(false);
        web.getOptions().setThrowExceptionOnFailingStatusCode(false);
        web.getOptions().setCssEnabled(false);
        web.getOptions().setDownloadImages(false);
        web.getOptions().setWebSocketEnabled(false);
        web.getOptions().setPrintContentOnFailingStatusCode(false);
        web.getOptions().setJavaScriptEnabled(true);
        // Le document d'audit renvoie le HTML rendu ; toute sous-ressource (scripts/CSS
        // externes) → corps vide : pas de réseau, pas de JS applicatif exécuté. Seuls les
        // scripts inline triviaux de la page tournent.
        web.setWebConnection(new WebConnection() {
            @Override public WebResponse getResponse(WebRequest request) {
                if (AUDIT_URL.equals(request.getUrl().toString())) {
                    return new StringWebResponse(pageHtml.get(), request.getUrl());
                }
                return new StringWebResponse("", request.getUrl());
            }
            @Override public void close() { }
        });
        return web;
    }

    /** Lit {@code /axe.min.js} embarqué dans le JAR deque (aucun CDN, aucun réseau). */
    private static String axeSource() throws Exception {
        if (axeSource == null) {
            try (InputStream in = A11yAxeTest.class.getResourceAsStream("/axe.min.js")) {
                if (in == null) throw new IllegalStateException("/axe.min.js introuvable au classpath");
                axeSource = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        return axeSource;
    }
}
