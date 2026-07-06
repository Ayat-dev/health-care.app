package com.clinic.backend.license;

import com.clinic.backend.desktop.DesktopPaths;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Sources <b>externes</b> et redondantes de la date de début d'essai (en plus de la base) :
 * un fichier hors du dossier programme et, sous Windows, le registre {@code HKCU}.
 * <p>
 * But : anti-triche <b>raisonnable</b> (pas blindé). {@link LicenseService} croise ces
 * sources avec la base et retient la date la plus <b>ancienne</b> — remettre une seule
 * source « à maintenant » ne rallonge donc pas l'essai, car une autre garde l'origine.
 * Toute opération est best-effort : un échec est logué et ignoré (jamais bloquant).
 */
@Component
@Slf4j
public class TrialStore {

    private static final String KEY = "trialStartedAt";
    private static final String REGISTRY_PATH = "HKCU\\Software\\ClinicApp";
    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase().contains("win");

    // ── Fichier ────────────────────────────────────────────────────────────────

    Optional<LocalDateTime> readFile() {
        Path file = DesktopPaths.home().resolve("license.trial");
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try (InputStream in = Files.newInputStream(file)) {
            Properties p = new Properties();
            p.load(in);
            return parseMillis(p.getProperty(KEY));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    void writeFile(LocalDateTime start) {
        try {
            Path home = DesktopPaths.home();
            Files.createDirectories(home);
            Properties p = new Properties();
            p.setProperty(KEY, String.valueOf(toMillis(start)));
            try (OutputStream out = Files.newOutputStream(home.resolve("license.trial"))) {
                p.store(out, "ClinicApp — marqueur d'essai. Ne pas modifier.");
            }
        } catch (Exception e) {
            log.debug("Écriture du marqueur d'essai (fichier) impossible : {}", e.getMessage());
        }
    }

    // ── Registre Windows (best-effort) ───────────────────────────────────────────

    Optional<LocalDateTime> readRegistry() {
        if (!WINDOWS) {
            return Optional.empty();
        }
        try {
            Process p = new ProcessBuilder("reg", "query", REGISTRY_PATH, "/v", KEY)
                    .redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes());
            p.waitFor(5, TimeUnit.SECONDS);
            // Ligne type : "    trialStartedAt    REG_SZ    1720300000000"
            for (String line : output.split("\\R")) {
                int idx = line.indexOf("REG_SZ");
                if (idx >= 0) {
                    return parseMillis(line.substring(idx + "REG_SZ".length()).trim());
                }
            }
        } catch (Exception e) {
            log.debug("Lecture du marqueur d'essai (registre) impossible : {}", e.getMessage());
        }
        return Optional.empty();
    }

    void writeRegistry(LocalDateTime start) {
        if (!WINDOWS) {
            return;
        }
        try {
            new ProcessBuilder("reg", "add", REGISTRY_PATH, "/v", KEY,
                    "/t", "REG_SZ", "/d", String.valueOf(toMillis(start)), "/f")
                    .redirectErrorStream(true).start()
                    .waitFor(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("Écriture du marqueur d'essai (registre) impossible : {}", e.getMessage());
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private static Optional<LocalDateTime> parseMillis(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            long millis = Long.parseLong(raw.trim());
            return Optional.of(LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static long toMillis(LocalDateTime dt) {
        return dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
