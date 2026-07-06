package com.clinic.backend.desktop;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Base de données du déploiement <b>desktop tout-en-un</b> (Phase 1).
 * <p>
 * Démarre un cluster <b>PostgreSQL embarqué</b> (zonky) in-process, avec un data-dir
 * <b>persistant</b> local ({@link DesktopPaths#dataDir()}) : à la première exécution
 * {@code initdb} crée le cluster, aux suivantes on le réutilise ({@code cleanDataDirectory=false}).
 * Le {@link DataSource} exposé pilote Flyway et JPA exactement comme en prod (même
 * moteur PostgreSQL → migrations inchangées).
 * <p>
 * Le répertoire d'extraction des binaires est fixé ({@link DesktopPaths#pgRuntimeDir()})
 * pour être déterministe : c'est là que {@link DesktopBackupService} retrouve {@code pg_dump}.
 * <p>
 * Cycle de vie géré par le conteneur : {@code destroyMethod = "close"} arrête proprement
 * le serveur à l'extinction (évite une corruption du cluster). Le hook JVM interne de
 * zonky est désactivé pour laisser Spring ordonner l'arrêt (DataSource fermé avant PG).
 */
@Configuration
@Profile("desktop")
@Slf4j
public class DesktopDatabaseConfig {

    @Bean(destroyMethod = "close")
    public EmbeddedPostgres embeddedPostgres(@Value("${app.desktop.db-port:15432}") int port) throws IOException {
        Path dataDir = DesktopPaths.dataDir();
        Path runtimeDir = DesktopPaths.pgRuntimeDir();
        Files.createDirectories(dataDir);
        Files.createDirectories(runtimeDir);

        boolean firstInstall = isEmptyDir(dataDir);
        log.info("PostgreSQL embarqué : démarrage sur le port {} (data-dir={}, {})",
                port, dataDir, firstInstall ? "première installation → initdb" : "base existante réutilisée");

        EmbeddedPostgres pg = EmbeddedPostgres.builder()
                .setPort(port)
                .setDataDirectory(dataDir)
                .setOverrideWorkingDirectory(runtimeDir.toFile())
                .setCleanDataDirectory(false)
                .setRegisterShutdownHook(false)
                .start();

        log.info("PostgreSQL embarqué prêt sur le port {}.", pg.getPort());
        return pg;
    }

    @Bean
    @Primary
    public DataSource dataSource(EmbeddedPostgres embeddedPostgres) {
        // Base « postgres » par défaut (utilisateur postgres, auth trust en local).
        return embeddedPostgres.getPostgresDatabase();
    }

    private static boolean isEmptyDir(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return true;
        }
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.findAny().isEmpty();
        }
    }
}
