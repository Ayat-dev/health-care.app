package com.clinic.backend.billing;

import com.clinic.backend.model.User;
import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A single encaissement against an invoice. Several payments may accumulate until the
 * invoice's {@code patientAmount} is covered. Mobile-money payments carry the operator
 * transaction number in {@code reference}.
 */
@Entity
@Table(name = "payments")
@Getter @Setter @NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Discriminant multi-tenant (P4.2) — renseigné par Hibernate via le resolver de tenant. */
    @TenantId
    @Column(name = "clinic_id")
    private Long clinicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    // ESPECES, AMANTY, MYNITA, VIREMENT, CARTE, ASSURANCE
    // (AMANTY/MYNITA = paiement mobile par QR marchand, confirmation manuelle)
    @Column(nullable = false, length = 30)
    private String method;

    @Column(length = 100)
    private String reference;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cashier_id")
    private User cashier;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
