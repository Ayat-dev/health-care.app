package com.clinic.backend.maternity;

import com.clinic.backend.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A single antenatal consultation (consultation prénatale — CPN) within a {@link MaternityRecord}.
 * Captures the obstetric monitoring for that visit (poids, tension, hauteur utérine, RCF…).
 */
@Entity
@Table(name = "prenatal_visits")
@Getter @Setter @NoArgsConstructor
public class PrenatalVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "maternity_record_id", nullable = false)
    private MaternityRecord maternityRecord;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private User doctor;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    @Column(name = "visit_number", nullable = false)
    private Integer visitNumber; // 1, 2, 3, 4… (CPN1..CPN4+)

    @Column(name = "gestational_age_weeks")
    private Integer gestationalAgeWeeks;

    @Column(name = "weight_kg", precision = 5, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "bp_systolic")
    private Integer bpSystolic;

    @Column(name = "bp_diastolic")
    private Integer bpDiastolic;

    @Column(name = "fetal_heart_rate")
    private Integer fetalHeartRate;

    @Column(name = "uterine_height_cm", precision = 4, scale = 1)
    private BigDecimal uterineHeightCm;

    @Column(length = 30)
    private String presentation; // CEPHALIQUE, SIEGE, TRANSVERSE…

    private Boolean edema;
    private Boolean proteinuria;

    @Column(name = "iron_supplemented")
    private Boolean ironSupplemented;

    @Column(name = "ttv_vaccine")
    private Boolean ttvVaccine; // vaccin antitétanique (VAT)

    @Column(columnDefinition = "TEXT")
    private String observations;

    @Column(columnDefinition = "TEXT")
    private String recommendations;

    @Column(name = "next_visit_date")
    private LocalDate nextVisitDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
