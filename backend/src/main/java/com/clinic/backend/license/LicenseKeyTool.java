package com.clinic.backend.license;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Outil d'émission de licences <b>hors-ligne</b>, exécuté par l'éditeur (jamais câblé au
 * runtime de l'application). La clé <b>privée</b> ne quitte jamais le poste de l'éditeur ;
 * seule la clé <b>publique</b> est embarquée dans le produit ({@code app.license.public-key}).
 * <p>
 * CLI à sous-commandes (Phase 4). Voir {@code docs/LICENSING-SALES.md}.
 * <pre>
 *   keygen --out &lt;dossier&gt;
 *   issue  --key &lt;privée&gt; --clinic "Nom" [--edition STANDARD] (--days N | --expires AAAA-MM-JJ)
 *          [--max-users N] [--features a,b] [--id LIC-...] [--registry fichier.jsonl | --no-registry]
 *   list   [--registry fichier.jsonl]
 *   verify --pubkey &lt;base64|fichier&gt; --token &lt;jeton|fichier&gt;
 * </pre>
 */
public final class LicenseKeyTool {

    private static final String DEFAULT_REGISTRY = "licenses.jsonl";

    private LicenseKeyTool() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usageAndExit();
        }
        Map<String, String> f = parseFlags(args);
        switch (args[0]) {
            case "keygen" -> keygen(f);
            case "issue" -> issue(f);
            case "list" -> list(f);
            case "verify" -> verify(f);
            default -> usageAndExit();
        }
    }

    // ── keygen ────────────────────────────────────────────────────────────────────

    private static void keygen(Map<String, String> f) throws Exception {
        Path outDir = Path.of(require(f, "out"));
        Files.createDirectories(outDir);

        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String priv = java.util.Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
        String pub = java.util.Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());

        Files.writeString(outDir.resolve("private.key"), priv);
        Files.writeString(outDir.resolve("public.key"), pub);

        System.out.println("Paire de clés Ed25519 générée dans " + outDir.toAbsolutePath());
        System.out.println("private.key : À GARDER SECRÈTE, hors du dépôt (sauvegardée chiffrée).");
        System.out.println();
        System.out.println("Clé publique à embarquer (app.license.public-key) :");
        System.out.println(pub);
    }

    // ── issue ─────────────────────────────────────────────────────────────────────

    private static void issue(Map<String, String> f) throws Exception {
        PrivateKey privateKey = LicenseCodec.privateKeyFromBase64(Files.readString(Path.of(require(f, "key"))));
        String clinic = require(f, "clinic");
        String edition = f.getOrDefault("edition", "STANDARD");

        LocalDate today = LocalDate.now();
        LocalDate expires;
        if (f.containsKey("expires")) {
            expires = LocalDate.parse(f.get("expires").trim());
        } else if (f.containsKey("days")) {
            expires = today.plusDays(Long.parseLong(f.get("days").trim()));
        } else {
            fail("Préciser --days N ou --expires AAAA-MM-JJ.");
            return; // inatteignable
        }
        Integer maxUsers = f.containsKey("max-users") ? Integer.parseInt(f.get("max-users").trim()) : null;
        List<String> features = f.containsKey("features")
                ? Arrays.stream(f.get("features").split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList()
                : List.of();
        String id = f.getOrDefault("id",
                "LIC-" + today.getYear() + "-" + Long.toHexString(System.currentTimeMillis()).toUpperCase());

        License license = new License(id, clinic, edition, features, maxUsers, today, expires);
        String token = LicenseCodec.encode(license, privateKey);

        // Enregistrement au registre (sauf --no-registry).
        if (!f.containsKey("no-registry")) {
            Path registryFile = Path.of(f.getOrDefault("registry", DEFAULT_REGISTRY));
            new LicenseRegistry(registryFile).append(LicenseRecord.of(license, token));
        }

        System.out.println("Licence émise :");
        System.out.println("  id       : " + id);
        System.out.println("  clinique : " + clinic);
        System.out.println("  édition  : " + edition);
        System.out.println("  valide   : " + today + " → " + expires);
        if (maxUsers != null) System.out.println("  maxUsers : " + maxUsers);
        System.out.println();
        System.out.println("Clé à remettre au client (coller dans /license) :");
        System.out.println(token);
    }

    // ── list ──────────────────────────────────────────────────────────────────────

    private static void list(Map<String, String> f) {
        Path registryFile = Path.of(f.getOrDefault("registry", DEFAULT_REGISTRY));
        List<LicenseRecord> records = new LicenseRegistry(registryFile).readAll();
        if (records.isEmpty()) {
            System.out.println("Aucune licence dans " + registryFile.toAbsolutePath());
            return;
        }
        System.out.printf("%-24s %-28s %-10s %-12s %-12s%n", "ID", "CLINIQUE", "ÉDITION", "ÉMISE", "EXPIRE");
        LocalDate today = LocalDate.now();
        for (LicenseRecord r : records) {
            String flag = r.expires() != null && today.isAfter(r.expires()) ? "  (expirée)" : "";
            System.out.printf("%-24s %-28s %-10s %-12s %-12s%s%n",
                    r.id(), truncate(r.clinic(), 28), r.edition(), r.issued(), r.expires(), flag);
        }
        System.out.println("(" + records.size() + " licence(s) — " + registryFile.toAbsolutePath() + ")");
    }

    // ── verify ────────────────────────────────────────────────────────────────────

    private static void verify(Map<String, String> f) throws Exception {
        PublicKey publicKey = LicenseCodec.publicKeyFromBase64(valueOrFile(require(f, "pubkey")));
        String token = valueOrFile(require(f, "token"));
        License lic = LicenseCodec.verify(token, publicKey);
        boolean expired = lic.isExpiredOn(LocalDate.now());
        System.out.println("Signature VALIDE. Contenu :");
        System.out.println("  id       : " + lic.id());
        System.out.println("  clinique : " + lic.clinic());
        System.out.println("  édition  : " + lic.edition());
        System.out.println("  valide   : " + lic.issued() + " → " + lic.expires()
                + (expired ? "  ⚠ EXPIRÉE" : "  (en cours)"));
        if (lic.maxUsers() != null) System.out.println("  maxUsers : " + lic.maxUsers());
        if (lic.features() != null && !lic.features().isEmpty()) System.out.println("  features : " + lic.features());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────

    /** Parse les drapeaux {@code --clé valeur}, {@code --clé=valeur} et booléens {@code --clé}. */
    private static Map<String, String> parseFlags(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if (!a.startsWith("--")) continue;
            String key = a.substring(2);
            String value = "true";
            int eq = key.indexOf('=');
            if (eq >= 0) {
                value = key.substring(eq + 1);
                key = key.substring(0, eq);
            } else if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                value = args[++i];
            }
            map.put(key, value);
        }
        return map;
    }

    private static String require(Map<String, String> f, String key) {
        String v = f.get(key);
        if (v == null || v.isBlank() || v.equals("true")) {
            fail("Argument manquant : --" + key);
        }
        return v;
    }

    /** Renvoie le contenu du fichier si l'argument est un chemin existant, sinon la valeur brute. */
    private static String valueOrFile(String s) throws Exception {
        Path p = Path.of(s);
        return Files.isRegularFile(p) ? Files.readString(p).trim() : s;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static void fail(String message) {
        System.err.println("Erreur : " + message);
        usageAndExit();
    }

    private static void usageAndExit() {
        System.err.println("""
                Outil de licences ClinicApp (éditeur, hors-ligne).

                  keygen --out <dossier>
                  issue  --key <privée> --clinic "Nom" [--edition STANDARD]
                         (--days N | --expires AAAA-MM-JJ)
                         [--max-users N] [--features a,b] [--id LIC-...]
                         [--registry fichier.jsonl | --no-registry]
                  list   [--registry fichier.jsonl]
                  verify --pubkey <base64|fichier> --token <jeton|fichier>
                """);
        System.exit(1);
    }
}
