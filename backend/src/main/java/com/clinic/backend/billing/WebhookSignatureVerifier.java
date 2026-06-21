package com.clinic.backend.billing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Authentifie les webhooks Mobile Money (P3.3) par signature HMAC-SHA256 du corps brut
 * avec un secret partagé (schéma « Stripe-like », commun aux agrégateurs).
 * <p>
 * L'agrégateur envoie {@code X-Webhook-Signature: <hex(HMAC-SHA256(body, secret))>}.
 * Comparaison à temps constant pour éviter les attaques temporelles.
 */
@Component
public class WebhookSignatureVerifier {

    private final byte[] secret;

    public WebhookSignatureVerifier(@Value("${app.webhook.secret}") String secret) {
        this.secret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
    }

    /** {@code true} si la signature fournie correspond au HMAC du corps. */
    public boolean isValid(String rawBody, String providedSignatureHex) {
        if (providedSignatureHex == null || providedSignatureHex.isBlank()) return false;
        String expected = hmacHex(rawBody);
        // Comparaison à temps constant (longueurs identiques requises).
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                providedSignatureHex.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    private String hmacHex(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] digest = mac.doFinal(body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(Character.forDigit((b >> 4) & 0xF, 16))
                                    .append(Character.forDigit(b & 0xF, 16));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de calculer la signature HMAC du webhook", e);
        }
    }
}
