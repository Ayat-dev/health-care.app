package com.clinic.backend.license;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;

/**
 * Outil d'émission de licences <b>hors-ligne</b>, exécuté par l'éditeur (jamais câblé au
 * runtime de l'application). La clé <b>privée</b> ne quitte jamais le poste de l'éditeur ;
 * seule la clé <b>publique</b> est embarquée dans le produit ({@code app.license.public-key}).
 * <p>
 * Base de la future CLI (Phase 4). Usage :
 * <pre>
 *   keygen &lt;dossierSortie&gt;
 *       → génère private.key (PKCS8 base64) + public.key (SPKI base64) et affiche la clé publique
 *   issue &lt;fichierCléPrivée&gt; &lt;clinique&gt; &lt;edition&gt; &lt;joursValidité&gt; [maxUsers]
 *       → affiche un jeton de licence signé
 * </pre>
 */
public final class LicenseKeyTool {

    private LicenseKeyTool() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usageAndExit();
        }
        switch (args[0]) {
            case "keygen" -> keygen(args);
            case "issue" -> issue(args);
            default -> usageAndExit();
        }
    }

    private static void keygen(String[] args) throws Exception {
        if (args.length < 2) {
            usageAndExit();
        }
        Path outDir = Path.of(args[1]);
        Files.createDirectories(outDir);

        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
        KeyPair pair = generator.generateKeyPair();
        String priv = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
        String pub = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());

        Files.writeString(outDir.resolve("private.key"), priv);
        Files.writeString(outDir.resolve("public.key"), pub);

        System.out.println("Paire de clés Ed25519 générée dans " + outDir.toAbsolutePath());
        System.out.println("Clé privée : private.key  (À GARDER SECRÈTE, hors du dépôt)");
        System.out.println();
        System.out.println("Clé publique à embarquer (app.license.public-key) :");
        System.out.println(pub);
    }

    private static void issue(String[] args) throws Exception {
        if (args.length < 5) {
            usageAndExit();
        }
        PrivateKey privateKey = LicenseCodec.privateKeyFromBase64(Files.readString(Path.of(args[1])));
        String clinic = args[2];
        String edition = args[3];
        int days = Integer.parseInt(args[4]);
        Integer maxUsers = args.length > 5 ? Integer.parseInt(args[5]) : null;

        LocalDate today = LocalDate.now();
        License license = new License(
                "LIC-" + today.getYear() + "-" + Long.toHexString(System.currentTimeMillis()).toUpperCase(),
                clinic, edition, List.of(), maxUsers, today, today.plusDays(days));

        String token = LicenseCodec.encode(license, privateKey);
        System.out.println("Licence émise pour « " + clinic + " » (" + edition
                + "), valide jusqu'au " + license.expires() + " :");
        System.out.println();
        System.out.println(token);
    }

    private static void usageAndExit() {
        System.err.println("""
                Usage :
                  keygen <dossierSortie>
                  issue  <fichierCléPrivée> <clinique> <edition> <joursValidité> [maxUsers]
                """);
        System.exit(1);
    }
}
