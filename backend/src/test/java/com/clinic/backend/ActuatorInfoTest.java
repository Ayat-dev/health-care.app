package com.clinic.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D2b — build-info (version) + git info exposés sur {@code /actuator/info} (public).
 * Les beans {@link BuildProperties}/{@link GitProperties} sont alimentés par les
 * fichiers générés au build (spring-boot:build-info + git-commit-id, cf. pom) ;
 * présents dans target/classes au moment des tests.
 * <p>
 * Contrairement à {@code /actuator/prometheus}, {@code /actuator/info} est un endpoint
 * core mappé sous MockMvc.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActuatorInfoTest {

    @Autowired MockMvc mvc;
    @Autowired BuildProperties buildProperties;
    @Autowired GitProperties gitProperties;

    @Test
    void build_et_git_properties_sont_chargees() {
        // Garde-fou build : si la génération échoue, les beans n'existeraient pas
        // (UnsatisfiedDependency) — l'injection elle-même prouve leur présence.
        assertThat(buildProperties.getVersion()).isEqualTo("0.0.1-SNAPSHOT");
        assertThat(buildProperties.getArtifact()).isEqualTo("medical-backend");
        assertThat(gitProperties.getBranch()).isNotBlank();
        assertThat(gitProperties.getShortCommitId()).isNotBlank();
    }

    @Test
    void actuator_info_expose_version_et_git() throws Exception {
        mvc.perform(get("/actuator/info"))
           .andExpect(status().isOk())
           .andExpect(content().string(containsString("\"build\"")))
           .andExpect(content().string(containsString("0.0.1-SNAPSHOT")))
           .andExpect(content().string(containsString("\"git\"")));
    }
}
