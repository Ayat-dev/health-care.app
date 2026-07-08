package com.clinic.backend.billing;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, Long> {

    /** Idempotence : une transaction d'agrégateur n'est traitée qu'une fois. */
    Optional<PaymentWebhookEvent> findByProviderAndTransactionId(String provider, String transactionId);

    /** Journal récent (suivi/rapprochement). */
    List<PaymentWebhookEvent> findTop50ByOrderByReceivedAtDesc();

    /**
     * Journal admin filtré (Z4a). Table <b>globale</b> (pas {@code @TenantId} — le webhook
     * arrive sans contexte de tenant) → requête transverse, réservée au SUPER_ADMIN côté contrôleur.
     * Chaque filtre est optionnel (null = ignoré). Limité via {@link Pageable}.
     */
    @Query("""
            SELECT e FROM PaymentWebhookEvent e
            WHERE (:provider IS NULL OR e.provider = :provider)
              AND (:status IS NULL OR e.status = :status)
              AND (CAST(:from AS timestamp) IS NULL OR e.receivedAt >= :from)
              AND (CAST(:to AS timestamp) IS NULL OR e.receivedAt <= :to)
            ORDER BY e.receivedAt DESC
            """)
    List<PaymentWebhookEvent> search(@Param("provider") String provider,
                                     @Param("status") String status,
                                     @Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to,
                                     Pageable pageable);

    /** Fournisseurs distincts présents (pour peupler le filtre). */
    @Query("SELECT DISTINCT e.provider FROM PaymentWebhookEvent e ORDER BY e.provider")
    List<String> distinctProviders();
}
