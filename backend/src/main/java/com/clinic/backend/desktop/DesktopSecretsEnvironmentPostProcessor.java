package com.clinic.backend.desktop;

import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Fournit, en mode <b>desktop tout-en-un</b>, les secrets applicatifs qui n'ont
 * aucune valeur par défaut ({@code app.jwt.secret}, {@code app.encryption.key},
 * {@code app.webhook.secret}, {@code app.monitoring.password}).
 * <p>
 * Sur un poste de clinique il n'y a ni {@code .env} ni administrateur système pour
 * injecter ces variables : on les <b>génère aléatoirement au premier démarrage</b> et
 * on les <b>persiste</b> dans {@code <home>/secrets.properties} (voir {@link DesktopPaths}).
 * Aux démarrages suivants on relit le même fichier → les jetons restent valides et,
 * surtout, la clé de chiffrement PHI reste stable (sinon les données chiffrées au repos
 * deviendraient illisibles).
 * <p>
 * Ne s'active que si le profil {@code desktop} est actif. On ne remplit que les clés
 * <b>absentes</b> de l'environnement → un opérateur peut toujours surcharger via une
 * variable d'environnement (précédence supérieure).
 * <p>
 * Ordonné après {@link ConfigDataEnvironmentPostProcessor} pour que les profils actifs
 * (fichiers {@code application-*.properties}) soient déjà résolus.
 */
public class DesktopSecretsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROFILE = "desktop";
    private static final String PROPERTY_SOURCE_NAME = "clinicapp-desktop-secrets";

    /** Secrets sans défaut requis au démarrage (cf. application-prod.properties). */
    private static final String[] SECRET_KEYS = {
            "app.jwt.secret",
            "app.encryption.key",
            "app.webhook.secret",
            "app.monitoring.password"
    };

    private final Log log;

    /** Constructeur invoqué par Spring Boot avec une fabrique de logs différés. */
    public DesktopSecretsEnvironmentPostProcessor(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(DesktopSecretsEnvironmentPostProcessor.class);
    }

    @Override
    public int getOrder() {
        // Juste après le traitement des fichiers de config → getActiveProfiles() est fiable.
        return ConfigDataEnvironmentPostProcessor.ORDER + 1000;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!isDesktopProfile(environment)) {
            return;
        }
        try {
            Path home = DesktopPaths.home();
            Files.createDirectories(home);

            Path secretsFile = DesktopPaths.secretsFile();
            Properties stored = new Properties();
            if (Files.exists(secretsFile)) {
                try (InputStream in = Files.newInputStream(secretsFile)) {
                    stored.load(in);
                }
            }

            boolean changed = false;
            for (String key : SECRET_KEYS) {
                String value = stored.getProperty(key);
                if (value == null || value.isBlank()) {
                    stored.setProperty(key, randomSecret());
                    changed = true;
                }
            }
            if (changed) {
                writeSecrets(secretsFile, stored);
                log.warn("Secrets desktop générés/complétés dans " + secretsFile
                        + " — NE PAS SUPPRIMER : la perte de app.encryption.key rend les données PHI illisibles.");
            }

            // Ne renseigne que ce qui n'est pas déjà fourni par l'environnement (override possible).
            Map<String, Object> resolved = new HashMap<>();
            for (String key : SECRET_KEYS) {
                if (environment.getProperty(key) == null) {
                    resolved.put(key, stored.getProperty(key));
                }
            }
            // Dossier des fichiers uploadés : sous le home (inscriptible), pas dans le dossier d'install.
            if (environment.getProperty("app.storage.upload-dir") == null) {
                resolved.put("app.storage.upload-dir", home.resolve("uploads").toString());
            }
            if (!resolved.isEmpty()) {
                // addLast : précédence minimale — tout ce qui existe déjà l'emporte.
                environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, resolved));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Impossible d'initialiser les secrets locaux du mode desktop", e);
        }
    }

    private boolean isDesktopProfile(ConfigurableEnvironment environment) {
        for (String profile : environment.getActiveProfiles()) {
            if (PROFILE.equalsIgnoreCase(profile)) {
                return true;
            }
        }
        // Filet de sécurité si l'EPP tourne avant la résolution complète des profils.
        String raw = environment.getProperty("spring.profiles.active", "");
        for (String profile : raw.split(",")) {
            if (PROFILE.equalsIgnoreCase(profile.trim())) {
                return true;
            }
        }
        return false;
    }

    private static String randomSecret() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static void writeSecrets(Path file, Properties props) throws IOException {
        try (OutputStream out = Files.newOutputStream(file)) {
            props.store(out, "ClinicApp — secrets locaux du poste (mode desktop). Confidentiel, ne pas partager.");
        }
        restrictPermissions(file);
    }

    /** Restreint l'accès au fichier au propriétaire quand le système de fichiers le permet (POSIX). */
    private static void restrictPermissions(Path file) {
        try {
            Set<PosixFilePermission> ownerOnly = PosixFilePermissions.fromString("rw-------");
            Files.setPosixFilePermissions(file, ownerOnly);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows (ACL) : best-effort. Le dossier %LOCALAPPDATA% est déjà propre à l'utilisateur.
        }
    }
}
