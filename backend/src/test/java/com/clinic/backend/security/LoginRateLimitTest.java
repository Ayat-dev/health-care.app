package com.clinic.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D1d — rate-limit IP du login (Bucket4j). Limite abaissée à 3/fenêtre via @TestPropertySource :
 * la 4ᵉ tentative depuis la même IP → 429 ; une autre IP garde son propre quota.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.security.login-rate-limit.max-attempts=3")
class LoginRateLimitTest {

    @Autowired MockMvc mvc;

    /** Tente un login (mauvais identifiants) depuis une IP donnée. */
    private org.springframework.test.web.servlet.ResultActions attempt(String ip) throws Exception {
        RequestPostProcessor fromIp = request -> { request.setRemoteAddr(ip); return request; };
        return mvc.perform(post("/api/auth/login").with(fromIp)
                .contentType(APPLICATION_JSON)
                .content("{\"username\":\"ghost-" + ip + "\",\"password\":\"wrong\"}"));
    }

    @Test
    void quatrieme_tentative_depuis_la_meme_ip_renvoie_429() throws Exception {
        String ip = "203.0.113.10";
        // 3 tentatives autorisées (échec d'auth = 401, PAS 429).
        for (int i = 0; i < 3; i++) {
            attempt(ip).andExpect(status().isUnauthorized());
        }
        // 4ᵉ : quota IP épuisé → 429 + Retry-After.
        attempt(ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void une_autre_ip_garde_son_propre_quota() throws Exception {
        String exhausted = "203.0.113.20";
        for (int i = 0; i < 4; i++) attempt(exhausted);
        attempt(exhausted).andExpect(status().isTooManyRequests());

        // IP distincte → bucket indépendant, toujours autorisée (401, pas 429).
        attempt("203.0.113.21").andExpect(status().isUnauthorized());
    }
}
