package com.clinic.backend.lab;

import com.clinic.backend.consultation.Consultation;
import com.clinic.backend.model.User;
import com.clinic.backend.patient.Patient;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A laboratory analysis order raised from a consultation (or standalone for a patient).
 * Carries one or more {@link LabRequestItem}s (the analyses to run). Status flow:
 * EN_ATTENTE → EN_COURS (results being entered) → VALIDE (validated by a doctor/biologist) → LIVRE.
 */
@Entity
@Table(name = "lab_requests")
@Getter @Setter @NoArgsConstructor
public class LabRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_number", nullable = false, unique = true, length = 25)
    private String requestNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consultation_id")
    private Consultation consultation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private User doctor;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt = LocalDateTime.now();

    @Column(nullable = false, length = 10)
    private String priority = "NORMAL"; // NORMAL, URGENT

    @Column(nullable = false, length = 20)
    private String status = "EN_ATTENTE"; // EN_ATTENTE, EN_COURS, VALIDE, LIVRE

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "labRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<LabRequestItem> items = new ArrayList<>();

    /** Keep both sides of the association in sync when adding an item. */
    public void addItem(LabRequestItem item) {
        item.setLabRequest(this);
        this.items.add(item);
    }
}
