package com.clinic.backend.hospitalization;

import com.clinic.backend.model.User;
import com.clinic.backend.patient.Patient;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A patient's stay in a bed. Lifecycle:
 * ADMIS → SORTI / DECEDE (discharge), or ADMIS → TRANSFERE (when moved to another room —
 * the current stay is closed as TRANSFERE and a fresh ADMIS stay is opened in the new room,
 * keeping a clean per-room history and freeing the previous bed).
 */
@Entity
@Table(name = "hospitalizations")
@Getter @Setter @NoArgsConstructor
public class Hospitalization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private User doctor;

    @Column(name = "admission_date", nullable = false)
    private LocalDateTime admissionDate = LocalDateTime.now();

    @Column(name = "discharge_date")
    private LocalDateTime dischargeDate;

    @Column(name = "admission_reason", columnDefinition = "TEXT", nullable = false)
    private String admissionReason;

    @Column(name = "diagnosis_on_discharge", columnDefinition = "TEXT")
    private String diagnosisOnDischarge;

    @Column(nullable = false, length = 20)
    private String status = "ADMIS"; // ADMIS, TRANSFERE, SORTI, DECEDE

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
