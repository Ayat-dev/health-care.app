package com.clinic.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test : le contexte Spring se charge (toutes les beans se câblent,
 * Flyway migre, Hibernate valide). Attrape les ruptures de wiring.
 */
@SpringBootTest
@ActiveProfiles("test")
class ClinicApplicationTests {

    @Test
    void contextLoads() {
        // si le contexte ne se charge pas, le test échoue
    }
}
