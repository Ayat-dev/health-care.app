package com.clinic.backend.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B3 — durcissement de l'app shell PWA. Le service worker précharge {@code SHELL_ASSETS}
 * à l'install via {@code cache.addAll}, qui est <b>atomique</b> : une seule entrée en 404
 * fait échouer toute l'installation du SW (donc l'offline). Ce test extrait la liste réelle
 * de {@code /sw.js} et vérifie que chaque ressource est servie publiquement en 200 — exactement
 * la classe de bug « on renomme/ajoute un JS mais on oublie le précache ».
 *
 * <p>Garde-fou sécurité : aucune entrée du précache ne doit pointer vers une page HTML / PHI /
 * endpoint d'auth (le SW ne met JAMAIS de page en cache — cf. règles de {@code sw.js}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PwaShellTest {

    @Autowired MockMvc mvc;

    private static final Pattern SHELL_BLOCK =
            Pattern.compile("SHELL_ASSETS\\s*=\\s*\\[(.*?)]", Pattern.DOTALL);
    private static final Pattern ENTRY = Pattern.compile("'([^']+)'");

    private List<String> shellAssets() throws Exception {
        String sw = mvc.perform(get("/sw.js"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Matcher block = SHELL_BLOCK.matcher(sw);
        assertThat(block.find()).as("bloc SHELL_ASSETS introuvable dans sw.js").isTrue();
        Matcher m = ENTRY.matcher(block.group(1));
        List<String> assets = new ArrayList<>();
        while (m.find()) assets.add(m.group(1));
        return assets;
    }

    /** Chaque ressource précachée existe et est publique (sinon addAll casse l'install). */
    @Test
    void chaque_ressource_de_l_app_shell_est_servie_en_200() throws Exception {
        List<String> assets = shellAssets();
        assertThat(assets).contains("/offline.html", "/css/app.css", "/manifest.webmanifest");
        for (String path : assets) {
            mvc.perform(get(path))
               .andExpect(status().isOk());
        }
    }

    /** Aucune PHI / page HTML / auth dans le précache : que des assets statiques sûrs. */
    @Test
    void le_precache_ne_contient_aucune_ressource_sensible() throws Exception {
        for (String path : shellAssets()) {
            assertThat(path)
                .doesNotStartWith("/api/")
                .doesNotStartWith("/fhir/")
                .doesNotStartWith("/uploads/")
                .doesNotStartWith("/patients")
                .doesNotStartWith("/login")
                .doesNotStartWith("/logout");
            // Que des ressources statiques (extension de fichier connue), jamais une route HTML.
            assertThat(path).matches(".*\\.(html|css|js|webmanifest|svg|png|ico)$");
        }
    }
}
