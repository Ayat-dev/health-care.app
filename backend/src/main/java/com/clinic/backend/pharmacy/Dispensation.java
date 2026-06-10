package com.clinic.backend.pharmacy;

import com.clinic.backend.consultation.Prescription;
import com.clinic.backend.model.User;
import com.clinic.backend.patient.Patient;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A stock-out event: drugs handed to a patient, either against a {@link Prescription}
 * (which is then flagged dispensed) or as an over-the-counter sale. Each line consumes
 * one {@link StockItem} batch.
 */
@Entity
@Table(name = "dispensations")
@Getter @Setter @NoArgsConstructor
public class Dispensation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prescription_id")
    private Prescription prescription;   // null = vente libre

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pharmacist_id", nullable = false)
    private User pharmacist;

    @Column(name = "dispensed_at", nullable = false)
    private LocalDateTime dispensedAt = LocalDateTime.now();

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @OneToMany(mappedBy = "dispensation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<DispensationItem> items = new ArrayList<>();

    /** Keep both sides of the association in sync when adding a line. */
    public void addItem(DispensationItem item) {
        item.setDispensation(this);
        this.items.add(item);
    }
}
