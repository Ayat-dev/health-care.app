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
 * i18n FR/EN/AR (P3.2) : la bascule {@code ?lang=} change effectivement la langue
 * rendue (bundles messages + LocaleChangeInterceptor + CookieLocaleResolver) et
 * l'arabe passe le document en RTL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class I18nTest {

    @Autowired MockMvc mvc;

    // ── Page de connexion (publique) ─────────────────────────────────────────

    @Test
    void login_par_defaut_en_francais() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Se connecter")));
    }

    @Test
    void login_bascule_en_anglais() throws Exception {
        mvc.perform(get("/login").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sign in")));
    }

    @Test
    void login_en_arabe_est_rtl() throws Exception {
        mvc.perform(get("/login").param("lang", "ar"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("dir=\"rtl\"")))
                .andExpect(content().string(containsString("تسجيل الدخول")));
    }

    // ── Chrome applicatif (sidebar i18n, principal authentifié) ──────────────

    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void sidebar_traduite_en_anglais() throws Exception {
        // P6 : le médecin a le module Tableau de bord → libellé de sidebar traduit (chrome i18n).
        mvc.perform(get("/dashboard").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Dashboard")));
    }

    /** Slice 13 (audit RTL fin) : une page applicative à fort tableau/formulaire (liste
     *  patients) passe bien le document en RTL + langue arabe une fois authentifié — c'est
     *  l'attribut {@code [dir="rtl"]} qui active les correctifs ciblés d'{@code app.css}
     *  (en-têtes de table {@code text-align:start}, flèche {@code <select>} à gauche,
     *  bordures d'accent en {@code border-inline-start}, etc.). */
    @Test
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void module_a_fort_tableau_en_arabe_est_rtl() throws Exception {
        mvc.perform(get("/patients").param("lang", "ar"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("dir=\"rtl\"")))
                .andExpect(content().string(containsString("lang=\"ar\"")));
    }
}
