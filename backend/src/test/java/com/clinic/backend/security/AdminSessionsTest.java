package com.clinic.backend.security;

import com.clinic.backend.dto.RefreshSessionDto;
import com.clinic.backend.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * D1c — vue admin « sessions actives » + révocation ciblée par appareil. Vérifie le gating
 * ADMIN de la page, et qu'une révocation ciblée invalide le refresh token de CET appareil
 * (son prochain /refresh → 401), sans dépendre des autres sessions.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSessionsTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository userRepository;
    @Autowired RefreshTokenService refreshTokenService;

    private Long adminId() {
        return userRepository.findByUsername("admin").orElseThrow().getId();
    }

    private Set<Long> activeSessionIds(Long userId) {
        Set<Long> ids = new HashSet<>();
        for (RefreshSessionDto s : refreshTokenService.listActiveForUser(userId)) ids.add(s.getId());
        return ids;
    }

    private String loginAndGetRefresh() throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("refreshToken").asText();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void admin_voit_la_page_sessions() throws Exception {
        mvc.perform(get("/admin/users/" + adminId() + "/sessions"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sessions actives")));
    }

    @Test
    @WithMockUser(username = "doc", roles = "MEDECIN")
    void non_admin_refuse_sur_la_page_sessions() throws Exception {
        mvc.perform(get("/admin/users/1/sessions"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void revoquer_une_session_invalide_le_refresh_de_cet_appareil() throws Exception {
        Long adminId = adminId();
        Set<Long> before = activeSessionIds(adminId);

        // Nouvelle session (appareil) pour admin.
        String refresh = loginAndGetRefresh();
        Set<Long> after = activeSessionIds(adminId);
        after.removeAll(before);
        assertSingleNewSession(after);
        Long sessionId = after.iterator().next();

        // L'admin révoque CETTE session via l'UI.
        mvc.perform(post("/admin/users/" + adminId + "/sessions/" + sessionId + "/revoke").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("success", "admin.sessions.revoked"));

        // L'appareil ne peut plus renouveler son accès → 401.
        mvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    private void assertSingleNewSession(Set<Long> newIds) {
        org.junit.jupiter.api.Assertions.assertEquals(1, newIds.size(),
                "le login doit créer exactement une session active, vu " + newIds.size());
    }
}
