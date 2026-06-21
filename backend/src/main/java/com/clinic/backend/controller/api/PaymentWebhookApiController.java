package com.clinic.backend.controller.api;

import com.clinic.backend.billing.InvalidWebhookSignatureException;
import com.clinic.backend.billing.PaymentWebhookService;
import com.clinic.backend.billing.PaymentWebhookService.WebhookResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Réception des webhooks Mobile Money (P3.3) — Orange Money / Wave / MTN MoMo.
 * <p>
 * {@code POST /api/payments/webhook/{provider}} (provider = orange|wave|mtn).
 * Authentifié par signature HMAC (entête {@code X-Webhook-Signature}), pas par JWT —
 * le chemin est donc {@code permitAll} sur la chaîne API stateless ({@code SecurityConfig}).
 * <p>
 * Sémantique HTTP voulue côté agrégateur :
 * <ul>
 *   <li><b>200</b> — notification acquittée (PROCESSED / DUPLICATE / REJECTED métier) : pas de rejeu</li>
 *   <li><b>401</b> — signature invalide</li>
 *   <li><b>400</b> — fournisseur inconnu ou corps illisible</li>
 * </ul>
 * Le corps brut est lu en {@code String} pour calculer le HMAC sur les octets exacts reçus.
 */
@RestController
@RequestMapping("/api/payments/webhook")
@RequiredArgsConstructor
public class PaymentWebhookApiController {

    private final PaymentWebhookService webhookService;

    @PostMapping("/{provider}")
    public ResponseEntity<WebhookResult> receive(
            @PathVariable String provider,
            @RequestBody(required = false) String rawBody,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature) {

        WebhookResult result = webhookService.process(provider, rawBody, signature);
        return ResponseEntity.ok(result);
    }

    // ── Traduction des erreurs en codes HTTP attendus par les agrégateurs ─────

    @ExceptionHandler(InvalidWebhookSignatureException.class)
    public ResponseEntity<Map<String, String>> onBadSignature(InvalidWebhookSignatureException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("status", "UNAUTHORIZED", "message", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> onBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("status", "BAD_REQUEST", "message", e.getMessage()));
    }
}
