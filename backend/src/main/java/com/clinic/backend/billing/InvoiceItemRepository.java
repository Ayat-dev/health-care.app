package com.clinic.backend.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agrégats « chiffre d'affaires » sur les lignes de facture (dashboard OWNER, C).
 * <p>
 * Repository dédié à {@link InvoiceItem} : les requêtes cross-entité déclarées sur
 * {@code InvoiceRepository} (racine {@code Invoice}) sont mal rendues par Hibernate 6
 * (cf. le quirk {@code SELECT new Payment()} documenté). Le tenant est filtré via
 * l'association {@code i.invoice} ({@code @TenantId} sur {@code Invoice}). Les factures
 * ANNULE sont exclues (pas de CA sur une facture annulée).
 */
public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, Long> {

    /** CA facturé par acte (libellé de ligne) sur la période, plus gros d'abord. */
    @Query("""
            SELECT i.description, SUM(i.totalPrice)
            FROM InvoiceItem i
            WHERE i.invoice.createdAt >= :from AND i.invoice.createdAt < :to
              AND i.invoice.status <> 'ANNULE'
            GROUP BY i.description
            ORDER BY SUM(i.totalPrice) DESC
            """)
    List<Object[]> revenueByAct(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** CA facturé par département (via act → department ; null = non classé) sur la période. */
    @Query("""
            SELECT d.name, SUM(i.totalPrice)
            FROM InvoiceItem i
            LEFT JOIN i.act a
            LEFT JOIN a.department d
            WHERE i.invoice.createdAt >= :from AND i.invoice.createdAt < :to
              AND i.invoice.status <> 'ANNULE'
            GROUP BY d.name
            ORDER BY SUM(i.totalPrice) DESC
            """)
    List<Object[]> revenueByDepartment(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
