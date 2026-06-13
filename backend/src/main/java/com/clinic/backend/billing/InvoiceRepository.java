package com.clinic.backend.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    /**
     * Full graph for a single invoice: patient/insurance/createdBy/consultation/hospitalization
     * + items→act. Only the {@code items} bag is fetched here; {@code payments} lazy-load inside
     * the service transaction (fetching both bags would trigger MultipleBagFetchException).
     */
    @Query("""
        SELECT DISTINCT inv FROM Invoice inv
        LEFT JOIN FETCH inv.patient
        LEFT JOIN FETCH inv.insurance
        LEFT JOIN FETCH inv.createdBy
        LEFT JOIN FETCH inv.consultation
        LEFT JOIN FETCH inv.hospitalization
        LEFT JOIN FETCH inv.items i
        LEFT JOIN FETCH i.act
        WHERE inv.id = :id
        """)
    Optional<Invoice> findWithRefsById(@Param("id") Long id);

    /**
     * Filtered list (header only — patient/insurance fetched). Any param may be null to skip.
     * The date window bounds created_at. Ordered most-recent first.
     */
    @Query("""
        SELECT inv FROM Invoice inv
        LEFT JOIN FETCH inv.patient
        LEFT JOIN FETCH inv.insurance
        WHERE (:from IS NULL OR inv.createdAt >= :from)
          AND (:to   IS NULL OR inv.createdAt <  :to)
          AND (:patientId IS NULL OR inv.patient.id = :patientId)
          AND (:status    IS NULL OR :status = '' OR inv.status = :status)
        ORDER BY inv.createdAt DESC
        """)
    List<Invoice> search(@Param("from") LocalDateTime from,
                         @Param("to") LocalDateTime to,
                         @Param("patientId") Long patientId,
                         @Param("status") String status);

    /** Chronological history for a patient's dossier (most recent first, header only). */
    @Query("""
        SELECT inv FROM Invoice inv
        LEFT JOIN FETCH inv.insurance
        WHERE inv.patient.id = :patientId
        ORDER BY inv.createdAt DESC
        """)
    List<Invoice> findByPatient(@Param("patientId") Long patientId);

    /** Highest sequence used for a numbering prefix (e.g. "FAC-2026-"); 0 if none. */
    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(inv.invoiceNumber, :start) AS int)), 0) " +
           "FROM Invoice inv WHERE inv.invoiceNumber LIKE :prefix%")
    int findMaxSequence(@Param("prefix") String prefix, @Param("start") int start);

    // ── Agrégats tableau de bord ────────────────────────────────────────────────
    @Query("SELECT COUNT(inv) FROM Invoice inv WHERE inv.status = :status")
    long countByStatus(@Param("status") String status);

    /** Somme de la part patient des factures non annulées (CA facturé). */
    @Query("SELECT COALESCE(SUM(inv.patientAmount), 0) FROM Invoice inv WHERE inv.status <> 'ANNULE'")
    BigDecimal totalInvoiced();

    /** Total déjà encaissé sur les factures non annulées. */
    @Query("SELECT COALESCE(SUM(inv.paidAmount), 0) FROM Invoice inv WHERE inv.status <> 'ANNULE'")
    BigDecimal totalCollected();

    /** Encaissements d'une période (somme des paiements). */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.paidAt >= :from AND p.paidAt < :to")
    BigDecimal collectedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * Unpaid (EN_ATTENTE / PARTIEL) invoices created on or before a cutoff — the overdue set
     * for the dunning job. Patient fetched for the SMS template (OSIV off).
     */
    @Query("""
        SELECT inv FROM Invoice inv
        LEFT JOIN FETCH inv.patient
        WHERE inv.status IN ('EN_ATTENTE', 'PARTIEL')
          AND inv.createdAt <= :cutoff
        ORDER BY inv.createdAt ASC
        """)
    List<Invoice> findOverdueUnpaid(@Param("cutoff") LocalDateTime cutoff);
}
