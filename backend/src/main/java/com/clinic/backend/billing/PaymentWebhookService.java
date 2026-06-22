package com.clinic.backend.billing;

import com.clinic.backend.dto.PaymentDto;
import com.clinic.backend.tenant.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Traite les notifications de paiement Mobile Money (P3.3) : vérifie la signature,
 * garantit l'idempotence, et applique automatiquement l'encaissement sur la facture.
 * <p>
 * <b>Volontairement non {@code @Transactional} au niveau classe</b> : chaque issue
 * (encaissement réussi, rejet métier, rejeu) doit être <em>journalisée</em> même quand
 * {@link BillingService#recordPayment} échoue et annule sa propre transaction. On délègue
 * donc l'encaissement à {@code recordPayment} (sa transaction) puis on persiste l'évènement
 * séparément, hors de cette transaction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentWebhookService {

    /** chemin d'URL → mode de paiement (= provider stocké). */
    private static final Map<String, String> PROVIDERS = Map.of(
            "orange", "ORANGE_MONEY",
            "wave",   "WAVE",
            "mtn",    "MTN_MOMO");

    private static final Set<String> SUCCESS_STATUSES =
            Set.of("SUCCESS", "SUCCESSFUL", "PAID", "COMPLETED", "OK");

    private final WebhookSignatureVerifier signatureVerifier;
    private final PaymentWebhookEventRepository eventRepository;
    private final InvoiceRepository invoiceRepository;
    private final BillingService billingService;
    private final ObjectMapper objectMapper;

    public WebhookResult process(String providerPath, String rawBody, String signature) {
        // 1. Authentification de l'agrégateur (HMAC) — avant toute écriture/parsing métier.
        if (!signatureVerifier.isValid(rawBody, signature)) {
            throw new InvalidWebhookSignatureException("Signature de webhook invalide");
        }

        // 2. Provider connu ?
        String provider = PROVIDERS.get(providerPath == null ? "" : providerPath.toLowerCase());
        if (provider == null) {
            throw new IllegalArgumentException("Fournisseur Mobile Money inconnu : " + providerPath);
        }

        // 3. Parse + validation de la clé d'idempotence.
        JsonNode body = parse(rawBody);
        String transactionId = text(body, "transactionId");
        if (transactionId == null) {
            throw new IllegalArgumentException("transactionId manquant");
        }
        String invoiceNumber = text(body, "invoiceNumber");
        String providerStatus = text(body, "status");
        BigDecimal amount = decimal(body, "amount");

        // 4. Idempotence — rejeu déjà vu ?
        Optional<PaymentWebhookEvent> existing =
                eventRepository.findByProviderAndTransactionId(provider, transactionId);
        if (existing.isPresent()) {
            return new WebhookResult("DUPLICATE", "Notification déjà traitée");
        }

        PaymentWebhookEvent ev = new PaymentWebhookEvent();
        ev.setProvider(provider);
        ev.setTransactionId(transactionId);
        ev.setInvoiceNumber(invoiceNumber);
        ev.setAmount(amount);
        ev.setProviderStatus(providerStatus);

        // 5. Statut non abouti → on journalise et on acquitte (pas de rejeu utile).
        if (providerStatus == null || !SUCCESS_STATUSES.contains(providerStatus.toUpperCase())) {
            return reject(ev, "Statut non abouti : " + providerStatus);
        }
        if (invoiceNumber == null) {
            return reject(ev, "invoiceNumber manquant");
        }
        if (amount == null || amount.signum() <= 0) {
            return reject(ev, "Montant invalide");
        }

        // Lookup GLOBAL (le webhook n'a pas de contexte de tenant) → on récupère la facture
        // toutes cliniques confondues, puis on applique l'encaissement sous SON tenant.
        Optional<Invoice> invoice = invoiceRepository.findByInvoiceNumber(invoiceNumber);
        if (invoice.isEmpty()) {
            return reject(ev, "Facture introuvable : " + invoiceNumber);
        }
        Invoice inv = invoice.get();
        ev.setInvoiceId(inv.getId());

        // 6. Encaissement (transaction propre de recordPayment, sous le tenant de la facture).
        //    Échec métier → rejet journalisé.
        try {
            PaymentDto dto = new PaymentDto();
            dto.setAmount(amount);
            dto.setMethod(provider);
            dto.setReference(transactionId);
            dto.setNotes("Encaissement Mobile Money (" + provider + ") — webhook " + transactionId);
            TenantContext.runAs(inv.getClinicId(), () -> billingService.recordPayment(inv.getId(), dto));

            ev.setStatus(PaymentWebhookEvent.Status.PROCESSED.name());
            save(ev);
            log.info("Webhook {} : facture {} encaissée de {} (txn {})",
                    provider, invoiceNumber, amount, transactionId);
            return new WebhookResult("PROCESSED", "Paiement appliqué");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return reject(ev, e.getMessage());
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private WebhookResult reject(PaymentWebhookEvent ev, String reason) {
        ev.setStatus(PaymentWebhookEvent.Status.REJECTED.name());
        ev.setErrorMessage(truncate(reason));
        save(ev);
        log.warn("Webhook {} rejeté (txn {}) : {}", ev.getProvider(), ev.getTransactionId(), reason);
        return new WebhookResult("REJECTED", reason);
    }

    /** Sauvegarde résiliente aux rejeux concurrents (course sur la contrainte unique). */
    private void save(PaymentWebhookEvent ev) {
        try {
            eventRepository.save(ev);
        } catch (DataIntegrityViolationException dup) {
            log.info("Webhook {} : rejeu concurrent ignoré (txn {})", ev.getProvider(), ev.getTransactionId());
        }
    }

    private JsonNode parse(String rawBody) {
        try {
            return objectMapper.readTree(rawBody == null ? "" : rawBody);
        } catch (Exception e) {
            throw new IllegalArgumentException("Corps JSON invalide");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        String s = v.asText().trim();
        return s.isEmpty() ? null : s;
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        try {
            return new BigDecimal(v.asText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > 255 ? s.substring(0, 255) : s;
    }

    /** Réponse renvoyée à l'agrégateur (sérialisée en JSON). */
    public record WebhookResult(String status, String message) {}
}
