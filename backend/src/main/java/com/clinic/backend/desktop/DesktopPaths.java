package com.clinic.backend.desktop;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Emplacements sur disque du déploiement <b>desktop tout-en-un</b> (Phase 1).
 * <p>
 * Tout vit sous un unique dossier « maison » persistant, hors du répertoire
 * d'installation du programme (droits d'écriture, survit à une mise à jour de
 * l'app) :
 * <ul>
 *   <li>Windows : {@code %LOCALAPPDATA%\ClinicApp} (ex. {@code C:\Users\X\AppData\Local\ClinicApp}) ;</li>
 *   <li>autres OS : {@code ~/.clinicapp}.</li>
 * </ul>
 * Surchargeable par la variable d'environnement {@code CLINICAPP_HOME} (utile pour
 * les tests, les postes multi-utilisateurs ou un data-dir sur un autre volume).
 * <p>
 * Résolution volontairement <b>statique et sans Spring</b> : elle est consommée à la
 * fois très tôt par {@link DesktopSecretsEnvironmentPostProcessor} (avant le contexte)
 * et par {@link DesktopDatabaseConfig} (au démarrage du cluster).
 */
public final class DesktopPaths {

    private DesktopPaths() {
    }

    /** Dossier racine de toutes les données locales de l'application. */
    public static Path home() {
        String override = System.getenv("CLINICAPP_HOME");
        if (override != null && !override.isBlank()) {
            return Paths.get(override.trim());
        }
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            String base = (localAppData != null && !localAppData.isBlank())
                    ? localAppData
                    : System.getProperty("user.home");
            return Paths.get(base, "ClinicApp");
        }
        return Paths.get(System.getProperty("user.home"), ".clinicapp");
    }

    /** Cluster PostgreSQL persistant (données métier). */
    public static Path dataDir() {
        return home().resolve("db");
    }

    /** Répertoire d'extraction des binaires PostgreSQL (déterministe → on y retrouve pg_dump). */
    public static Path pgRuntimeDir() {
        return home().resolve("pg-runtime");
    }

    /** Secrets locaux auto-générés (clés JWT / chiffrement / webhook / monitoring). */
    public static Path secretsFile() {
        return home().resolve("secrets.properties");
    }

    /** Dossier des sauvegardes logiques (dumps SQL). */
    public static Path backupsDir() {
        return home().resolve("backups");
    }
}
