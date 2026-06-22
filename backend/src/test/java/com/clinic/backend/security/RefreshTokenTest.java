package com.clinic.backend.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Refresh tokens + révocation JWT (P4.4). Exerce le vrai cycle via /api/auth/* :
 * login → access court + refresh long, rotation, détection de réutilisation, logout,
 * et révocation immédiate des access tokens via logout-all (bump de version).
 * Compte amorcé : admin/admin123.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefreshTokenTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private static final String PROTECTED = "/api/departments"; // ouvert à tout authentifié

    private JsonNode login() throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private int callProtected(String accessToken) throws Exception {
        return mvc.perform(get(PROTECTED).header("Authorization", "Bearer " + accessToken))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void login_renvoie_un_access_et_un_refresh_token() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void access_token_ouvre_l_api_puis_refresh_le_fait_tourner() throws Exception {
        JsonNode tokens = login();
        String access = tokens.get("accessToken").asText();
        String refresh = tokens.get("refreshToken").asText();

        // L'access token donne accès à l'API.
        org.junit.jupiter.api.Assertions.assertEquals(200, callProtected(access));

        // Rotation : on obtient un nouvel access + un nouveau refresh.
        String refreshBody = mvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String newAccess = json.readTree(refreshBody).get("accessToken").asText();
        org.junit.jupiter.api.Assertions.assertEquals(200, callProtected(newAccess));
    }

    @Test
    void reutiliser_un_refresh_token_deja_rotate_est_refuse() throws Exception {
        JsonNode tokens = login();
        String refresh = tokens.get("refreshToken").asText();

        // Première rotation : OK.
        mvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isOk());

        // Rejouer l'ancien refresh (révoqué par la rotation) → 401 (vol présumé).
        mvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_revoque_le_refresh_token() throws Exception {
        JsonNode tokens = login();
        String refresh = tokens.get("refreshToken").asText();

        mvc.perform(post("/api/auth/logout")
                        .contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isOk());

        // Le refresh révoqué ne peut plus être échangé.
        mvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_all_invalide_immediatement_l_access_token() throws Exception {
        JsonNode tokens = login();
        String access = tokens.get("accessToken").asText();

        org.junit.jupiter.api.Assertions.assertEquals(200, callProtected(access));

        // Révocation globale : bump de la version de token.
        mvc.perform(post("/api/auth/logout-all")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());

        // Le MÊME access token (encore non expiré) est désormais rejeté.
        int status = callProtected(access);
        org.junit.jupiter.api.Assertions.assertTrue(status == 401 || status == 403,
                "access token attendu rejeté après logout-all, reçu " + status);
    }
}
