package com.clinic.backend.billing;

import com.clinic.backend.catalog.ActCatalog;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One billable line on an invoice. {@code act} is an optional reference to the act
 * catalogue (used to pre-fill description/price); the line keeps its own snapshot of
 * description/unit price so historical invoices stay stable if the catalogue changes.
 */
@Entity
@Table(name = "invoice_items")
@Getter @Setter @NoArgsConstructor
public class InvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "act_id")
    private ActCatalog act;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(nullable = false)
    private int quantity = 1;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "total_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice = BigDecimal.ZERO;
}
