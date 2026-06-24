package com.clinic.backend.billing;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /**
     * Facture « ouverte » (accumulatrice, P5.1) du patient, s'il y en a une — verrou pessimiste
     * pour sérialiser le find-or-create de {@code addCharge} (anti-course en dev/H2 ; en prod
     * l'index partiel unique fait foi). Filtrée par tenant via {@code @TenantId}. Renvoie une
     * liste par prudence (au plus un élément attendu) pour éviter un {@code NonUniqueResult}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT inv FROM Invoice inv
        WHERE inv.patient.id = :patientId AND inv.open = true AND inv.status <> 'ANNULE'
        ORDER BY inv.id ASC
        """)
    List<Invoice> findOpenByPatient(@Param("patientId") Long patientId);

    /**
     * Vrai si l'acte (sourceType, sourceId) est déjà facturé sur une facture non annulée
     * (idempotence de l'auto-facturation — un acte ne se facture jamais deux fois).
     */
    @Query("""
        SELECT COUNT(it) > 0 FROM InvoiceItem it
        WHERE it.sourceType = :sourceType AND it.sourceId = :sourceId
          AND it.invoice.status <> 'ANNULE'
        """)
    boolean existsBilledSource(@Param("sourceType") String sourceType, @Param("sourceId") Long sourceId);

    /**
     * File d'attente caisse (P5.1) : factures encore à encaisser (reste dû > 0), patient chargé.
     * C'est la « pile » que le caissier sélectionne — la plus ancienne d'abord.
     */
    @Query("""
        SELECT inv FROM Invoice inv
        LEFT JOIN FETCH inv.patient
        WHERE inv.status IN ('EN_ATTENTE', 'PARTIEL')
        ORDER BY inv.createdAt ASC
        """)
    List<Invoice> findCashierQueue();

    /** Chronological history for a patient's dossier (most recent first, header only). */
    @Query("""
        SELECT inv FROM Invoice inv
        LEFT JOIN FETCH inv.insurance
        WHERE inv.patient.id = :patientId
        ORDER BY inv.createdAt DESC
        """)
    List<Invoice> findByPatient(@Param("patientId") Long patientId);

    /**
     * Recherche exacte par numéro de facture (webhook Mobile Money P3.3). Requête <b>native</b>
     * volontairement GLOBALE (non filtrée par @TenantId, P4.2) : le webhook n'a pas de contexte
     * de tenant ; il récupère la facture toutes cliniques confondues puis applique l'encaissement
     * sous le tenant de la facture trouvée.
     */
    @Query(value = "SELECT * FROM invoices WHERE invoice_number = :invoiceNumber", nativeQuery = true)
    Optional<Invoice> findByInvoiceNumber(@Param("invoiceNumber") String invoiceNumber);

    /** Recherche globale (P3.5) : factures dont le numéro contient {@code q}, patient chargé (OSIV off). */
    @Query("""
        SELECT inv FROM Invoice inv
        LEFT JOIN FETCH inv.patient
        WHERE LOWER(inv.invoiceNumber) LIKE LOWER(CONCAT('%', :q, '%'))
        ORDER BY inv.createdAt DESC
        """)
    List<Invoice> searchByNumber(@Param("q") String q, Pageable pageable);

    /**
     * Highest sequence used for a numbering prefix (e.g. "FAC-2026-"); 0 if none.
     * Native + GLOBAL (non filtré par @TenantId, P4.2) : numéros de facture uniques entre cliniques.
     */
    @Query(value = "SELECT COALESCE(MAX(CAST(SUBSTR(invoice_number, :start) AS INTEGER)), 0) " +
                   "FROM invoices WHERE invoice_number LIKE CONCAT(:prefix, '%')", nativeQuery = true)
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
