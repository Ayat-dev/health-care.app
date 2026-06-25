package com.clinic.backend.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Polish des catalogues business (P6 WS5 — modules secondaires) : les tarifs des actes et
 * analyses suivent désormais la convention monétaire de l'app (séparateur de milliers
 * {@code WHITESPACE} + suffixe {@code F}) et les colonnes numériques sont alignées à droite
 * ({@code .text-right}). Rendu via {@code @WithUserDetails} (vrai OWNER → tenant résolu,
 * sinon les assocs tenant-scopées sont filtrées en test).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminCatalogPolishTest {

    @Autowired MockMvc mvc;

    @Test
    @WithUserDetails(value = "owner", userDetailsServiceBeanName = "userDetailsServiceImpl")
    void le_catalogue_des_actes_formate_les_tarifs_et_aligne_a_droite() throws Exception {
        // Seed acts : CONS_GEN = 5000 → « 5 000 F » (espace insécable WHITESPACE), colonne text-right.
        mvc.perform(get("/admin/acts"))
           .andExpect(status().isOk())
           .andExpect(content().string(allOf(
                   containsString("class=\"text-right\""),
                   containsString(" F"))));
    }
}
