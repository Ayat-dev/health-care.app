package com.clinic.backend;

import com.clinic.backend.department.Department;
import com.clinic.backend.department.DepartmentRepository;
import com.clinic.backend.patient.Patient;
import com.clinic.backend.patient.PatientRepository;
import com.clinic.backend.tenant.ClinicRepository;
import com.clinic.backend.tenant.TenantContext;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A1 — Validation sur un VRAI PostgreSQL (vs H2 par défaut) via Testcontainers.
 * <p>
 * Prouve, de façon <b>rejouable et CI-safe</b>, que (1) tout l'historique Flyway
 * s'applique sur le moteur de prod, (2) le cloisonnement multi-tenant {@code @TenantId}
 * fonctionne sur PostgreSQL, (3) les UNIQUE composites {@code (clinic_id, code)} autorisent
 * la réutilisation d'un code entre cliniques.
 * <p>
 * Le profil {@code test} fournit les secrets (JWT/chiffrement/webhook/monitoring) et seede
 * 2 cliniques (DataInitializer, {@code @Profile("!prod")}). La datasource H2 du profil est
 * <b>écrasée</b> par {@link DynamicPropertySource} (précédence supérieure) vers le conteneur PG.
 * <p>
 * {@code @Testcontainers(disabledWithoutDocker = true)} → la classe est <b>skippée</b> (pas en
 * échec) si Docker est absent, donc {@code mvnd test} reste vert sans Docker.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@Tag("testcontainers")
class PostgresMigrationTenancyTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired Flyway flyway;
    @Autowired PatientRepository patientRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired ClinicRepository clinicRepository;

    private Long clinic1() { return clinicRepository.findByCodeIgnoreCase("CENTRALE").orElseThrow().getId(); }
    private Long clinic2() { return clinicRepository.findByCodeIgnoreCase("PLATEAU").orElseThrow().getId(); }

    @AfterEach
    void clear() { TenantContext.clear(); }

    @Test
    void tout_l_historique_flyway_s_applique_sur_postgresql() {
        // Le contexte s'est chargé avec ddl-auto=validate → Hibernate valide déjà contre PG.
        // On confirme en plus que Flyway a appliqué tout l'historique (≥ V30) sans pending/failed.
        MigrationInfoService info = flyway.info();
        assertThat(info.current()).isNotNull();
        assertThat(info.current().getVersion().compareTo(MigrationVersion.fromVersion("30")))
                .as("Flyway doit être au moins à V30 sur PostgreSQL")
                .isGreaterThanOrEqualTo(0);
        assertThat(info.pending()).as("aucune migration en attente").isEmpty();
        assertThat(info.applied().length).as("migrations appliquées").isGreaterThanOrEqualTo(30);

        // Sanity : on tourne bien sur PostgreSQL, pas sur H2.
        assertThat(POSTGRES.getJdbcUrl()).startsWith("jdbc:postgresql:");
    }

    @Test
    void cloisonnement_tenant_sur_postgresql() {
        List<String> dansClinic1 = TenantContext.callAs(clinic1(),
                () -> patientRepository.findAll().stream().map(Patient::getRecordNumber).toList());
        List<String> dansClinic2 = TenantContext.callAs(clinic2(),
                () -> patientRepository.findAll().stream().map(Patient::getRecordNumber).toList());

        assertThat(dansClinic1).contains("PAT-2026-00001").doesNotContain("PAT-PLT-00001");
        assertThat(dansClinic2).contains("PAT-PLT-00001").doesNotContain("PAT-2026-00001");

        // Accès par id cloisonné (filtre @TenantId au find).
        Long p1Id = TenantContext.callAs(clinic1(),
                () -> patientRepository.findByRecordNumberAndDeletedAtIsNull("PAT-2026-00001").orElseThrow().getId());
        assertThat(TenantContext.callAs(clinic2(), () -> patientRepository.findById(p1Id))).isEmpty();
        assertThat(TenantContext.callAs(clinic1(), () -> patientRepository.findById(p1Id))).isPresent();
    }

    @Test
    void unique_composite_clinic_code_autorise_la_reutilisation_entre_cliniques() {
        // Code neuf (absent des seeds des 2 cliniques) créé dans CHAQUE clinique : autorisé par
        // l'UNIQUE composite (clinic_id, code). Si l'UNIQUE était global, le 2ᵉ insert échouerait.
        String code = "A1_REUSE";
        TenantContext.runAs(clinic1(), () -> {
            Department d = new Department(); d.setCode(code); d.setName("CENTRALE — réutilisation");
            departmentRepository.saveAndFlush(d);
        });
        TenantContext.runAs(clinic2(), () -> {
            Department d = new Department(); d.setCode(code); d.setName("PLATEAU — réutilisation");
            departmentRepository.saveAndFlush(d);
        });

        // Chaque clinique voit exactement 1 département avec ce code (réutilisation cloisonnée).
        assertThat(TenantContext.callAs(clinic1(),
                () -> departmentRepository.findAll().stream().filter(x -> code.equals(x.getCode())).count()))
                .isEqualTo(1);
        assertThat(TenantContext.callAs(clinic2(),
                () -> departmentRepository.findAll().stream().filter(x -> code.equals(x.getCode())).count()))
                .isEqualTo(1);
    }
}
