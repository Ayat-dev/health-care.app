package com.clinic.backend.billing;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Notification de paiement Mobile Money reçue d'un agrégateur (P3.3).
 * Sert de piste d'audit ET de garde-fou d'idempotence : la contrainte unique
 * {@code (provider, transaction_id)} empêche qu'un rejeu crée un double paiement.
 *
 * @see com.clinic.backend.billing.PaymentWebhookService
 */
@Entity
@Table(name = "payment_webhook_events")
@Getter @Setter @NoArgsConstructor
public class PaymentWebhookEvent {

    /** Issue de la notification : payée et appliquée, rejetée, ou rejeu. */
    public enum Status { RECEIVED, PROCESSED, REJECTED, DUPLICATE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ORANGE_MONEY, WAVE, MTN_MOMO (= mode de paiement appliqué). */
    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "transaction_id", nullable = false, length = 100)
    private String transactionId;

    @Column(name = "invoice_number", length = 25)
    private String invoiceNumber;

    @Column(name = "invoice_id")
    private Long invoiceId;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "provider_status", length = 30)
    private String providerStatus;

    @Column(nullable = false, length = 20)
    private String status = Status.RECEIVED.name();

    @Column(name = "error_message", length = 255)
    private String errorMessage;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt = LocalDateTime.now();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
