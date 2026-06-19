package com.clinic.backend.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Catalogue CIM-10 (P2.2) : l'auto-complétion trouve par code et par libellé ;
 * les écritures sont réservées à l'ADMIN et le code en doublon est rejeté (400).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Icd10CatalogTest {

    @Autowired MockMvc mvc;

    @Test
    @WithMockUser(username = "doc", roles = "MEDECIN")
    void recherche_par_code_trouve_le_diagnostic() throws Exception {
        mvc.perform(get("/api/icd10/search").param("q", "J06"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].code").value("J06.9"));
    }

    @Test
    @WithMockUser(username = "doc", roles = "MEDECIN")
    void recherche_par_libelle_trouve_le_diagnostic() throws Exception {
        // « diab » → Diabète de type 2 (E11)
        mvc.perform(get("/api/icd10/search").param("q", "diab"))
           .andExpect(status().isOk())
           .andExpect(content().string(org.hamcrest.Matchers.containsString("E11")));
    }

    @Test
    @WithMockUser(username = "doc", roles = "MEDECIN")
    void un_non_admin_ne_peut_pas_creer_un_code() throws Exception {
        String body = "{\"code\":\"Z99\",\"title\":\"Test\"}";
        mvc.perform(post("/api/icd10").contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void code_en_doublon_renvoie_400() throws Exception {
        // I10 est déjà amorcé par V16
        String body = "{\"code\":\"I10\",\"title\":\"Doublon\"}";
        mvc.perform(post("/api/icd10").contentType(MediaType.APPLICATION_JSON).content(body))
           .andExpect(status().isBadRequest());
    }
}
