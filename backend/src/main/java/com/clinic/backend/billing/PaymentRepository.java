package com.clinic.backend.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * Encaissements d'une période [from, to) pour le rapport de caisse, ordonnés par heure.
     * Les associations invoice/patient/cashier sont chargées paresseusement lors du mapping
     * DTO, qui s'exécute dans la transaction du service (OSIV désactivé).
     */
    List<Payment> findByPaidAtGreaterThanEqualAndPaidAtLessThanOrderByPaidAtAsc(
            LocalDateTime from, LocalDateTime to);

    /**
     * Paiements d'une journée pour les modes fournis (rapprochement QR, Z4b) — tenant-scopés
     * (Payment est {@code @TenantId}). Mapping DTO dans la transaction du service.
     */
    List<Payment> findByMethodInAndPaidAtGreaterThanEqualAndPaidAtLessThanOrderByPaidAtAsc(
            Collection<String> methods, LocalDateTime from, LocalDateTime to);

    /** Encaissements d'une période regroupés par mode de paiement — [method, sum]. */
    @Query("""
        SELECT p.method, COALESCE(SUM(p.amount), 0) FROM Payment p
        WHERE p.paidAt >= :from AND p.paidAt < :to
        GROUP BY p.method
        ORDER BY SUM(p.amount) DESC
        """)
    List<Object[]> sumByMethodBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
