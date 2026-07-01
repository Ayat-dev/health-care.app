package com.clinic.backend.web;

import com.clinic.backend.model.User;
import com.clinic.backend.repository.UserRepository;
import com.clinic.backend.security.mfa.MfaService;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tier E3 — la porte MFA (interceptor) : un compte MFA-activé est redirigé vers /mfa/challenge
 * après login tant que le second facteur n'est pas validé pour la session ; un code valide
 * débloque la session ; un compte SANS MFA n'est jamais gêné (opt-in).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MfaFlowTest {

    @Autowired MockMvc mvc;
    @Autowired MfaService mfaService;
    @Autowired UserRepository userRepository;

    private User user(String username) {
        return userRepository.findByUsername(username).orElseThrow();
    }

    private Authentication as(User u) {
        return new UsernamePasswordAuthenticationToken(u, "x", u.getAuthorities());
    }

    private String validCode(String secret) throws Exception {
        return new DefaultCodeGenerator().generate(secret, Math.floorDiv(new SystemTimeProvider().getTime(), 30));
    }

    /** Active le MFA sur admin et renvoie son secret TOTP. */
    private String enableMfaForAdmin() throws Exception {
        Long id = user("admin").getId();
        MfaService.SetupData setup = mfaService.beginSetup(id);
        mfaService.confirmSetup(id, validCode(setup.secret()));
        return setup.secret();
    }

    @Test
    void compte_mfa_active_est_redirige_vers_le_challenge() throws Exception {
        enableMfaForAdmin();
        User admin = user("admin"); // rechargé → mfaEnabled=true
        mvc.perform(get("/admin/users").with(authentication(as(admin))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mfa/challenge"));
    }

    @Test
    void un_code_valide_debloque_la_session() throws Exception {
        String secret = enableMfaForAdmin();
        User admin = user("admin");
        MockHttpSession session = new MockHttpSession();

        // Challenge OK → redirigé vers la page d'accueil du rôle (ADMIN = /admin/users).
        mvc.perform(post("/mfa/challenge").param("code", validCode(secret))
                        .session(session).with(csrf()).with(authentication(as(admin))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));

        // Même session : la porte laisse désormais passer.
        mvc.perform(get("/admin/users").session(session).with(authentication(as(admin))))
                .andExpect(status().isOk());
    }

    @Test
    void mauvais_code_reste_sur_le_challenge() throws Exception {
        enableMfaForAdmin();
        User admin = user("admin");
        mvc.perform(post("/mfa/challenge").param("code", "000000")
                        .with(csrf()).with(authentication(as(admin))))
                .andExpect(status().isOk()); // re-rend le formulaire avec l'erreur (pas de redirection)
    }

    @Test
    void compte_sans_mfa_n_est_pas_gene() throws Exception {
        User admin = user("admin"); // MFA non activé
        mvc.perform(get("/admin/users").with(authentication(as(admin))))
                .andExpect(status().isOk());
    }
}
