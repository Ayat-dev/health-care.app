package com.clinic.backend.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Recherche globale (P3.5) : l'agrégation multi-sources et le filtrage par rôle.
 * Le LABORANTIN n'a ni le module PATIENTS ni BILLING → ces catégories n'apparaissent
 * jamais pour lui, exactement comme la sidebar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GlobalSearchTest {

    @Autowired MockMvc mvc;

    @Test
    // P6 : l'ADMIN n'a plus le module PATIENTS → c'est un soignant qui trouve un patient.
    @WithUserDetails(value = "dr.martin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void medecin_trouve_un_patient() throws Exception {
        mvc.perform(get("/search/suggest").param("q", "Diallo"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("PATIENT")))
                .andExpect(content().string(containsString("Diallo")));
    }

    @Test
    @WithUserDetails(value = "admin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void admin_trouve_un_module_par_navigation() throws Exception {
        // "utilisa" matche le libellé du module Utilisateurs (technique, ADMIN) → NAV /admin/users
        mvc.perform(get("/search/suggest").param("q", "utilisa"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("NAV")))
                .andExpect(content().string(containsString("/admin/users")));
    }

    @Test
    @WithUserDetails(value = "laborantin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void laborantin_ne_voit_pas_les_patients() throws Exception {
        // Pas de module PATIENTS pour ce rôle → aucune catégorie patient
        mvc.perform(get("/search/suggest").param("q", "Diallo"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Diallo"))));
    }

    @Test
    @WithUserDetails(value = "admin", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void page_de_resultats_rend_200() throws Exception {
        mvc.perform(get("/search").param("q", "Diallo"))
                .andExpect(status().isOk());
    }
}
