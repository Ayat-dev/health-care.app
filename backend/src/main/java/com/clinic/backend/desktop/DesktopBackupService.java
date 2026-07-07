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
     * Chemin explicite vers {@code pg_dump(.exe)} (surcharge facultative). Les binaires PG
     * embarqués (zonky, « réduits ») <b>n'incluent pas</b> les outils client (pg_dump/psql),
     * mais <b>fournissent toutes ses DLL</b> (libpq, libssl/crypto, libintl, zlib…). L'installeur
     * n'a donc qu'à livrer le <b>seul</b> {@code pg_dump.exe} officiel (même version majeure, 14.x) ;
     * il est retrouvé automatiquement à côté du jar (layout jpackage {@code app\pg_dump.exe}), et
     * ses DLL sont résolues en injectant le répertoire des binaires zonky dans le {@code PATH} du
     * processus fils. Ordre de résolution : cette surcharge → le répertoire d'extraction zonky →
     * le voisinage du jar. À défaut, sauvegardes désactivées (dégradation propre).
     */
    @Value("${app.desktop.backup.pg-dump-path:}")
    private String pgDumpPathOverride;

    private volatile Path pgDumpExecutable;
    /** Répertoire des binaires zonky (postgres.exe + DLL) — injecté au PATH du fils pour résoudre libpq & co. */
    private volatile Path pgLibDir;

    public DesktopBackupService(EmbeddedPostgres embeddedPostgres) {
        this.embeddedPostgres = embeddedPostgres;
    }

    @PostConstruct
    void init() {
        this.pgLibDir = locatePgLibDir();
        this.pgDumpExecutable = resolvePgDump();
        if (pgDumpExecutable == null) {
            log.warn("pg_dump introuvable (ni app.desktop.backup.pg-dump-path, ni sous {}, ni à côté du jar) — "
                            + "sauvegardes automatiques désactivées. Fournir un pg_dump officiel au packaging (-PgDump).",
                    DesktopPaths.pgRuntimeDir());
        } else {
            log.info("Sauvegardes automatiques activées via {} (DLL : {}).", pgDumpExecutable,
                    pgLibDir != null ? pgLibDir : "voisines de l'exécutable");
        }
    }

    private Path resolvePgDump() {
        if (pgDumpPathOverride != null && !pgDumpPathOverride.isBlank()) {
            Path explicit = java.nio.file.Paths.get(pgDumpPathOverride.trim());
            return Files.isRegularFile(explicit) ? explicit : null;
        }
        Path inRuntime = locatePgDump();          // déjà présent dans le répertoire zonky (DLL colocalisées)
        if (inRuntime != null) {
            return inRuntime;
        }
        return findBundledPgDump();               // livré par l'installeur, à côté du jar
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
            // pg_dump.exe livré seul → ses DLL (libpq…) vivent dans le répertoire des binaires
            // zonky : on l'ajoute en tête du PATH du fils pour qu'elles soient résolues. (Sans
            // effet — et sans risque — si pg_dump a déjà ses DLL à côté de lui.)
            if (pgLibDir != null) {
                String path = pb.environment().getOrDefault("PATH", "");
                pb.environment().put("PATH", pgLibDir + java.io.File.pathSeparator + path);
            }

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
        return walkFor(DesktopPaths.pgRuntimeDir(), "pg_dump", "pg_dump.exe")
                .max(Comparator.comparing(Path::toString)) // + récent si plusieurs versions extraites
                .orElse(null);
    }

    /**
     * Répertoire des binaires zonky (celui qui contient {@code postgres.exe}) : il héberge toutes
     * les DLL dont {@code pg_dump} a besoin (libpq, libssl/crypto, libintl, zlib, lz4…). On l'injecte
     * dans le {@code PATH} du processus fils quand le {@code pg_dump.exe} livré n'a pas ses DLL à côté.
     */
    private static Path locatePgLibDir() {
        return walkFor(DesktopPaths.pgRuntimeDir(), "postgres", "postgres.exe")
                .map(Path::getParent)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static Stream<Path> walkFor(Path root, String... names) {
        if (!Files.isDirectory(root)) {
            return Stream.empty();
        }
        java.util.Set<String> want = java.util.Set.of(names);
        try {
            // Collecté puis refermé : on ne peut pas rendre un Stream adossé à un Files.walk fermé.
            try (Stream<Path> walk = Files.walk(root)) {
                return walk.filter(Files::isRegularFile)
                        .filter(p -> want.contains(p.getFileName().toString().toLowerCase(Locale.ROOT)))
                        .toList().stream();
            }
        } catch (IOException e) {
            return Stream.empty();
        }
    }

    /**
     * {@code pg_dump.exe} livré par l'installeur, retrouvé à côté du jar en cours d'exécution
     * (layout jpackage : le jar et {@code pg_dump.exe} cohabitent dans {@code app\}). Ses DLL sont
     * fournies séparément via {@link #locatePgLibDir()}.
     * <p>
     * Le dossier du jar est dérivé en priorité de {@code java.class.path} (avec {@code -jar}, c'est
     * le fat jar lui-même) : {@code getCodeSource().getLocation()} renvoie une URL <i>nested</i>
     * ({@code jar:nested:…}) dans un fat jar Spring Boot 3.2+, que {@code Paths.get(URI)} rejette.
     * On ajoute des repli sûrs : {@code CodeSource} best-effort, le répertoire de travail et son
     * sous-dossier {@code app\} (cas d'un lancement depuis la racine d'installation).
     */
    private static Path findBundledPgDump() {
        java.util.LinkedHashSet<Path> dirs = new java.util.LinkedHashSet<>();

        // 1) java.class.path : chaque entrée .jar → son dossier parent (fiable avec -jar).
        String cp = System.getProperty("java.class.path", "");
        for (String entry : cp.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
            if (entry.toLowerCase(Locale.ROOT).endsWith(".jar")) {
                Path parent = java.nio.file.Paths.get(entry).toAbsolutePath().getParent();
                if (parent != null) {
                    dirs.add(parent);
                }
            }
        }
        // 2) CodeSource (best-effort ; échoue proprement sur une URL nested).
        try {
            var loc = DesktopBackupService.class.getProtectionDomain().getCodeSource().getLocation();
            if (loc != null) {
                Path self = java.nio.file.Paths.get(loc.toURI());
                dirs.add(Files.isDirectory(self) ? self : self.getParent());
            }
        } catch (Exception ignore) {
            // URL nested / pas de CodeSource exploitable — on s'appuie sur les autres sources
        }
        // 3) Répertoire de travail et son sous-dossier app\.
        Path cwd = java.nio.file.Paths.get("").toAbsolutePath();
        dirs.add(cwd);
        dirs.add(cwd.resolve("app"));

        for (Path dir : dirs) {
            if (dir == null) {
                continue;
            }
            for (String name : new String[]{"pg_dump.exe", "pg_dump"}) {
                Path cand = dir.resolve(name);
                if (Files.isRegularFile(cand)) {
                    return cand;
                }
            }
        }
        return null;
    }
}
