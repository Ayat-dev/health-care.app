package com.clinic.backend.pharmacy;

import com.clinic.backend.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A physical batch of a {@link Drug} held in stock: its own batch number, expiry date,
 * quantity on hand, and prices. Dispensations decrement {@code quantity}; FIFO picks the
 * earliest-expiring non-expired batch first.
 */
@Entity
@Table(name = "stock_items")
@Getter @Setter @NoArgsConstructor
public class StockItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "drug_id", nullable = false)
    private Drug drug;

    @Column(name = "batch_number", length = 50)
    private String batchNumber;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(nullable = false)
    private int quantity = 0;

    @Column(name = "quantity_alert", nullable = false)
    private int quantityAlert = 10;      // seuil d'alerte de rupture

    @Column(name = "purchase_price", precision = 12, scale = 2)
    private BigDecimal purchasePrice;

    @Column(name = "selling_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal sellingPrice;

    @Column(length = 150)
    private String supplier;

    @Column(name = "received_at", nullable = false)
    private LocalDate receivedAt = LocalDate.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "received_by")
    private User receivedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
