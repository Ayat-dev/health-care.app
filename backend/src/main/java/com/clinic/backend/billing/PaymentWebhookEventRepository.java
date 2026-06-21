package com.clinic.backend.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, Long> {

    /** Idempotence : une transaction d'agrégateur n'est traitée qu'une fois. */
    Optional<PaymentWebhookEvent> findByProviderAndTransactionId(String provider, String transactionId);

    /** Journal récent (suivi/rapprochement). */
    List<PaymentWebhookEvent> findTop50ByOrderByReceivedAtDesc();
}
