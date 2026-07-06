package com.clinic.backend.desktop;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Sauvegardes locales automatiques du cluster PostgreSQL embarqué (Phase 1, mode desktop).
 * <p>
 * Produit un <b>dump logique SQL</b> via {@code pg_dump} (fourni par la distribution PG
 * embarquée, retrouvé dans {@link DesktopPaths#pgRuntimeDir()}) dans
 * {@link DesktopPaths#backupsDir()}, puis applique une rétention. Un dump logique est sûr
 * à chaud (contrairement à une copie de fichiers du data-dir d'un serveur en marche).
 * <p>
 * Robustesse : aucune exception ne remonte — un échec de sauvegarde <b>ne doit jamais</b>
 * empêcher l'application de fonctionner (même posture que {@code StockAlertService} /
 * {@code NotificationService}). On logue et on continue.
 */
@Component
@org.springframework.context.annotation.Profile("desktop")
@Slf4j
public class DesktopBackupService {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.ROOT);

    private final EmbeddedPostgres embeddedPostgres;

    @Value("${app.desktop.backup.retention-days:14}")
    private int retentionDays;

    /** Délai max d'exécution de pg_dump avant abandon (base locale → rapide). */
    @Value("${app.desktop.backup.timeout-seconds:120}")
    private int timeoutSeconds;

    /**
     * Chemin explicite vers {@code pg_dump(.exe)}. À renseigner au packaging (Phase 2) :
     * les binaires PG embarqués (zonky, « réduits ») <b>n'incluent pas</b> les outils client
     * (pg_dump/psql), donc l'installeur doit fournir un pg_dump officiel (même version majeure).
     * Si vide, on tente une recherche sous le répertoire d'extraction ; à défaut, les
     * sauvegardes automatiques restent désactivées (dégradation propre).
     */
    @Value("${app.desktop.backup.pg-dump-path:}")
    private String pgDumpPathOverride;

    private volatile Path pgDumpExecutable;

    public DesktopBackupService(EmbeddedPostgres embeddedPostgres) {
        this.embeddedPostgres = embeddedPostgres;
    }

    @PostConstruct
    void init() {
        this.pgDumpExecutable = resolvePgDump();
        if (pgDumpExecutable == null) {
            log.warn("pg_dump introuvable (ni app.desktop.backup.pg-dump-path, ni sous {}) — "
                            + "sauvegardes automatiques désactivées. Fournir un pg_dump officiel au packaging.",
                    DesktopPaths.pgRuntimeDir());
        } else {
            log.info("Sauvegardes automatiques activées via {}", pgDumpExecutable);
        }
    }

    private Path resolvePgDump() {
        if (pgDumpPathOverride != null && !pgDumpPathOverride.isBlank()) {
            Path explicit = java.nio.file.Paths.get(pgDumpPathOverride.trim());
            return Files.isRegularFile(explicit) ? explicit : null;
        }
        return locatePgDump();
    }

    /** Une sauvegarde peu après le démarrage, pour toujours disposer d'un point récent. */
    @EventListener(ApplicationReadyEvent.class)
    public void backupOnStartup() {
        runBackupSafely("démarrage");
    }

    /** Sauvegarde planifiée (par défaut chaque nuit à 02:00). */
    @Scheduled(cron = "${app.desktop.backup.cron:0 0 2 * * *}")
    public void scheduledBackup() {
        runBackupSafely("planifiée");
    }

    private void runBackupSafely(String trigger) {
        if (pgDumpExecutable == null) {
            return;
        }
        try {
            Path backupsDir = DesktopPaths.backupsDir();
            Files.createDirectories(backupsDir);
            Path target = backupsDir.resolve("clinicapp-" + LocalDateTime.now().format(STAMP) + ".sql");

            ProcessBuilder pb = new ProcessBuilder(
                    pgDumpExecutable.toString(),
                    "-h", "localhost",
                    "-p", String.valueOf(embeddedPostgres.getPort()),
                    "-U", "postgres",
                    "-d", "postgres",
                    "-f", target.toString());
            pb.redirectErrorStream(true);
            pb.environment().put("PGPASSWORD", "postgres");

            Process process = pb.start();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("Sauvegarde ({}) : pg_dump a dépassé {}s, abandon.", trigger, timeoutSeconds);
                Files.deleteIfExists(target);
                return;
            }
            if (process.exitValue() != 0) {
                log.warn("Sauvegarde ({}) : pg_dump a échoué (code {}).", trigger, process.exitValue());
                Files.deleteIfExists(target);
                return;
            }
            log.info("Sauvegarde ({}) écrite : {} ({} Ko).", trigger, target, Files.size(target) / 1024);
            purgeOldBackups(backupsDir);
        } catch (IOException e) {
            log.warn("Sauvegarde ({}) impossible : {}", trigger, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Sauvegarde ({}) interrompue.", trigger);
        }
    }

    private void purgeOldBackups(Path backupsDir) {
        Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));
        try (Stream<Path> files = Files.list(backupsDir)) {
            List<Path> stale = files
                    .filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .filter(p -> lastModified(p).isBefore(cutoff))
                    .toList();
            for (Path p : stale) {
                Files.deleteIfExists(p);
                log.info("Sauvegarde expirée supprimée : {}", p.getFileName());
            }
        } catch (IOException e) {
            log.warn("Purge des sauvegardes impossible : {}", e.getMessage());
        }
    }

    private static Instant lastModified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toInstant();
        } catch (IOException e) {
            return Instant.now();
        }
    }

    /** Recherche récursive de pg_dump(.exe) dans le répertoire d'extraction des binaires PG. */
    private static Path locatePgDump() {
        Path runtimeDir = DesktopPaths.pgRuntimeDir();
        if (!Files.isDirectory(runtimeDir)) {
            return null;
        }
        try (Stream<Path> walk = Files.walk(runtimeDir)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.equals("pg_dump") || name.equals("pg_dump.exe");
                    })
                    .max(Comparator.comparing(Path::toString)) // + récent si plusieurs versions extraites
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }
}
