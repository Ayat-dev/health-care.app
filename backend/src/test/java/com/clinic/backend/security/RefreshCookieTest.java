package com.clinic.backend.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D1a — refresh token en cookie HttpOnly pour le front web. Vérifie que le mode cookie
 * (login avec {@code "cookie":"true"}) ne renvoie jamais le refresh token en JSON, le pose
 * en cookie {@code HttpOnly}, et que /refresh + /logout consomment ce cookie. Le mode JSON
 * (API/desktop) reste couvert par {@link RefreshTokenTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefreshCookieTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private static final String COOKIE = RefreshCookieManager.COOKIE_NAME;
    private static final String PROTECTED = "/api/departments";

    private MvcResult loginWithCookie() throws Exception {
        return mvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\",\"cookie\":\"true\"}"))
                .andExpect(status().isOk())
                // L'access token reste en JSON (court, en mémoire JS), MAIS jamais le refresh.
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(cookie().exists(COOKIE))
                .andExpect(cookie().httpOnly(COOKIE, true))
                .andReturn();
    }

    @Test
    void login_cookie_pose_un_cookie_httponly_et_aucun_refresh_en_json() throws Exception {
        loginWithCookie();
    }

    @Test
    void refresh_via_cookie_fait_tourner_le_jeton_et_repose_le_cookie() throws Exception {
        MvcResult login = loginWithCookie();
        Cookie refreshCookie = login.getResponse().getCookie(COOKIE);
        assertNotNull(refreshCookie);

        MvcResult refresh = mvc.perform(post("/api/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(cookie().exists(COOKIE))
                .andReturn();

        // Le nouvel access token ouvre bien l'API.
        String access = json.readTree(refresh.getResponse().getContentAsString())
                .get("accessToken").asText();
        assertEquals(200, mvc.perform(get(PROTECTED).header("Authorization", "Bearer " + access))
                .andReturn().getResponse().getStatus());

        // Rotation effective : le cookie a changé de valeur.
        Cookie rotated = refresh.getResponse().getCookie(COOKIE);
        assertNotNull(rotated);
        assertNotEquals(refreshCookie.getValue(), rotated.getValue());

        // Rejouer l'ANCIEN cookie (révoqué par la rotation) → 401 + cookie effacé (maxAge 0).
        mvc.perform(post("/api/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().maxAge(COOKIE, 0));
    }

    @Test
    void logout_via_cookie_revoque_et_efface_le_cookie() throws Exception {
        MvcResult login = loginWithCookie();
        Cookie refreshCookie = login.getResponse().getCookie(COOKIE);
        assertNotNull(refreshCookie);

        mvc.perform(post("/api/auth/logout").cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge(COOKIE, 0));

        // Le refresh révoqué ne peut plus être échangé.
        mvc.perform(post("/api/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized());
    }
}
