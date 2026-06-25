package com.clinic.backend.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Assistant {@code /setup} dans un contexte DÉJÀ installé (profil « test » : des comptes
 * sont seedés par DataInitializer). On vérifie que l'assistant est <b>verrouillé</b> :
 * il renvoie vers {@code /login} au lieu de permettre une seconde installation, et qu'il
 * est bien accessible sans authentification (la redirection vient du contrôleur — URL
 * relative {@code /login} — pas du point d'entrée de sécurité).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SetupWizardWebTest {

    @Autowired MockMvc mvc;

    @Test
    void get_setup_verrouille_redirige_vers_login() throws Exception {
        mvc.perform(get("/setup"))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/login")); // redirection contrôleur ⇒ /setup est bien permitAll
    }

    @Test
    void post_setup_verrouille_redirige_vers_login() throws Exception {
        mvc.perform(post("/setup").with(csrf())
                        .param("adminUsername", "pirate")
                        .param("adminPassword", "motdepasse1")
                        .param("adminPasswordConfirm", "motdepasse1")
                        .param("clinicName", "Clinique pirate"))
           .andExpect(status().is3xxRedirection())
           .andExpect(redirectedUrl("/login"));
    }
}
