package com.clinic.backend.maternity;

import com.clinic.backend.model.User;
import com.clinic.backend.patient.Patient;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A pregnancy dossier (carnet de grossesse) — one long-lived record per patiente. Unlike a single
 * encounter it spans the whole pregnancy: it carries many {@link PrenatalVisit}s (CPN) and is closed
 * out by an accouchement. Status flow: EN_COURS → ACCOUCHEE (delivery recorded) → CLOTURE.
 */
@Entity
@Table(name = "maternity_records")
@Getter @Setter @NoArgsConstructor
public class MaternityRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false, unique = true)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id")
    private User doctor;

    private Integer gravidity; // nombre de grossesses
    private Integer parity;    // nombre d'accouchements

    @Column(name = "last_period_date")
    private LocalDate lastPeriodDate;

    @Column(name = "expected_due_date")
    private LocalDate expectedDueDate;

    @Column(name = "delivery_date")
    private LocalDate deliveryDate;

    @Column(name = "delivery_type", length = 20)
    private String deliveryType; // NATUREL, CESARIENNE, FORCEPS

    @Column(name = "delivery_outcome", length = 20)
    private String deliveryOutcome; // VIVANT, MORT_NE, AVORTEMENT

    @Column(name = "newborn_weight_g")
    private Integer newbornWeightG;

    @Column(name = "newborn_apgar1")
    private Integer newbornApgar1;

    @Column(name = "newborn_apgar5")
    private Integer newbornApgar5;

    @Column(name = "newborn_gender", length = 10)
    private String newbornGender; // M, F

    @Column(columnDefinition = "TEXT")
    private String complications;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false, length = 20)
    private String status = "EN_COURS"; // EN_COURS, ACCOUCHEE, CLOTURE

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "maternityRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("visitDate ASC, visitNumber ASC")
    private List<PrenatalVisit> visits = new ArrayList<>();

    @PreUpdate
    void onUpdate() { this.updatedAt = LocalDateTime.now(); }

    /** Keep both sides of the association in sync when adding a CPN visit. */
    public void addVisit(PrenatalVisit visit) {
        visit.setMaternityRecord(this);
        this.visits.add(visit);
    }
}
