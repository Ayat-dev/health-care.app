package com.clinic.backend.license;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Encode / vérifie une clé de licence.
 * <p>
 * Format du jeton : {@code base64url(payloadJson) + "." + base64url(signatureEd25519)}.
 * La signature porte sur les octets UTF-8 du JSON. La vérification est <b>hors-ligne</b>
 * (clé publique uniquement) : aucune connexion réseau, robuste sur les postes à faible
 * connectivité. L'algorithme Ed25519 est natif du JDK (≥ 15), sans BouncyCastle.
 * <p>
 * Classe volontairement <b>sans dépendance Spring</b> : réutilisable telle quelle par
 * l'outil d'émission hors-ligne ({@link LicenseKeyTool}).
 */
public final class LicenseCodec {

    private static final String ALGORITHM = "Ed25519";
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private LicenseCodec() {
    }

    /** Émet un jeton signé (outil d'émission — nécessite la clé privée de l'éditeur). */
    public static String encode(License license, PrivateKey privateKey) {
        try {
            byte[] payload = MAPPER.writeValueAsBytes(license);
            byte[] signature = sign(payload, privateKey);
            return B64.encodeToString(payload) + "." + B64.encodeToString(signature);
        } catch (Exception e) {
            throw new LicenseException("Émission de la licence impossible : " + e.getMessage(), e);
        }
    }

    /**
     * Vérifie la signature et décode le contenu. Ne juge PAS l'expiration (rôle du service).
     *
     * @throws LicenseException si le format est invalide ou la signature ne correspond pas
     */
    public static License verify(String token, PublicKey publicKey) {
        if (token == null || token.isBlank()) {
            throw new LicenseException("Clé de licence vide.");
        }
        String cleaned = token.trim().replaceAll("\\s", "");
        int dot = cleaned.indexOf('.');
        if (dot <= 0 || dot == cleaned.length() - 1) {
            throw new LicenseException("Format de clé de licence invalide.");
        }
        try {
            byte[] payload = B64D.decode(cleaned.substring(0, dot));
            byte[] signature = B64D.decode(cleaned.substring(dot + 1));
            if (!verifySignature(payload, signature, publicKey)) {
                throw new LicenseException("Signature de licence invalide (clé non authentique).");
            }
            License license = MAPPER.readValue(payload, License.class);
            if (license.expires() == null) {
                throw new LicenseException("Licence sans date d'expiration.");
            }
            return license;
        } catch (LicenseException e) {
            throw e;
        } catch (Exception e) {
            throw new LicenseException("Clé de licence illisible.", e);
        }
    }

    // ── Primitives Ed25519 ───────────────────────────────────────────────────────

    private static byte[] sign(byte[] data, PrivateKey privateKey) throws Exception {
        Signature sig = Signature.getInstance(ALGORITHM);
        sig.initSign(privateKey);
        sig.update(data);
        return sig.sign();
    }

    private static boolean verifySignature(byte[] data, byte[] signature, PublicKey publicKey) throws Exception {
        Signature sig = Signature.getInstance(ALGORITHM);
        sig.initVerify(publicKey);
        sig.update(data);
        return sig.verify(signature);
    }

    public static PublicKey publicKeyFromBase64(String base64Spki) {
        try {
            byte[] der = Base64.getDecoder().decode(base64Spki.trim());
            return KeyFactory.getInstance(ALGORITHM).generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new LicenseException("Clé publique de licence mal configurée.", e);
        }
    }

    public static PrivateKey privateKeyFromBase64(String base64Pkcs8) {
        try {
            byte[] der = Base64.getDecoder().decode(base64Pkcs8.trim());
            return KeyFactory.getInstance(ALGORITHM).generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new LicenseException("Clé privée de licence illisible.", e);
        }
    }

    static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
