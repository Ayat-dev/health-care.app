package com.clinic.backend.billing;

import com.clinic.backend.consultation.Consultation;
import com.clinic.backend.hospitalization.Hospitalization;
import com.clinic.backend.insurance.InsuranceProvider;
import com.clinic.backend.model.User;
import com.clinic.backend.patient.Patient;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A patient's bill. Aggregates billable lines (from a consultation, a hospitalization,
 * or entered manually) and applies the insurance coverage to split the amount between
 * the insurer and the patient. The status is money-driven:
 *   EN_ATTENTE → PARTIEL → PAYE (cumulative payments vs {@code patientAmount}), or ANNULE.
 */
@Entity
@Table(name = "invoices")
@Getter @Setter @NoArgsConstructor
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_number", nullable = false, unique = true, length = 25)
    private String invoiceNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consultation_id")
    private Consultation consultation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hospitalization_id")
    private Hospitalization hospitalization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insurance_id")
    private InsuranceProvider insurance;

    @Column(name = "insurance_coverage_percent", precision = 5, scale = 2)
    private BigDecimal insuranceCoveragePercent = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "insurance_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal insuranceAmount = BigDecimal.ZERO;

    @Column(name = "patient_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal patientAmount = BigDecimal.ZERO;

    @Column(name = "paid_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 20)
    private String status = "EN_ATTENTE"; // EN_ATTENTE, PARTIEL, PAYE, ANNULE

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<InvoiceItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("paidAt ASC, id ASC")
    private List<Payment> payments = new ArrayList<>();

    @PreUpdate
    void onUpdate() { this.updatedAt = LocalDateTime.now(); }

    /** Keep both sides of the association in sync when adding a billable line. */
    public void addItem(InvoiceItem item) {
        item.setInvoice(this);
        this.items.add(item);
    }

    /** Keep both sides of the association in sync when recording a payment. */
    public void addPayment(Payment payment) {
        payment.setInvoice(this);
        this.payments.add(payment);
    }

    /** Reste à payer = part patient − déjà encaissé (jamais négatif). */
    @Transient
    public BigDecimal getBalanceDue() {
        BigDecimal balance = patientAmount.subtract(paidAmount);
        return balance.signum() < 0 ? BigDecimal.ZERO : balance;
    }
}
