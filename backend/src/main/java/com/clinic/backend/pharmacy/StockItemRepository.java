package com.clinic.backend.pharmacy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface StockItemRepository extends JpaRepository<StockItem, Long> {

    /** All batches (drug eagerly fetched for DTO mapping — OSIV is off), newest expiry last. */
    @Query("""
        SELECT s FROM StockItem s
        JOIN FETCH s.drug d
        ORDER BY d.name ASC, s.expiryDate ASC
        """)
    List<StockItem> findAllWithDrug();

    /**
     * Batches available to dispense a drug, in FIFO order: earliest expiry first
     * (premier périmé = premier sorti), then earliest reception. Excludes empty and
     * already-expired batches.
     */
    @Query("""
        SELECT s FROM StockItem s
        JOIN FETCH s.drug d
        WHERE s.drug.id = :drugId
          AND s.quantity > 0
          AND s.expiryDate >= :today
        ORDER BY s.expiryDate ASC, s.receivedAt ASC, s.id ASC
        """)
    List<StockItem> findAvailableForDrug(@Param("drugId") Long drugId, @Param("today") LocalDate today);

    /** Batches at or below their alert threshold (still holding stock). */
    @Query("""
        SELECT s FROM StockItem s
        JOIN FETCH s.drug d
        WHERE s.quantity <= s.quantityAlert
          AND s.expiryDate >= :today
        ORDER BY s.quantity ASC, d.name ASC
        """)
    List<StockItem> findLowStock(@Param("today") LocalDate today);

    /** Non-empty batches expiring within the window [today, until]. */
    @Query("""
        SELECT s FROM StockItem s
        JOIN FETCH s.drug d
        WHERE s.quantity > 0
          AND s.expiryDate >= :today
          AND s.expiryDate <= :until
        ORDER BY s.expiryDate ASC
        """)
    List<StockItem> findExpiringBetween(@Param("today") LocalDate today, @Param("until") LocalDate until);

    /** Total retail value of stock on hand (Σ quantity × selling_price). */
    @Query("SELECT COALESCE(SUM(s.quantity * s.sellingPrice), 0) FROM StockItem s WHERE s.quantity > 0")
    BigDecimal totalStockValue();
}
