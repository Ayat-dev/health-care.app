package com.clinic.backend.radiology;

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
 * An imaging order raised from a consultation (or standalone for a patient). Carries one or
 * more {@link RadiologyRequestItem}s (the exams to perform), a single {@link RadiologyReport}
 * (the radiologist's compte-rendu) and any {@link RadiologyImage}s attached.
 *
 * <p>Same request → result shape as the Lab module, but the "result" here is a narrative
 * report + images for the whole request rather than numeric values per item. Status flow:
 * EN_ATTENTE → EN_COURS (report being written) → VALIDE (signed off) → LIVRE.
 */
@Entity
@Table(name = "radiology_requests")
@Getter @Setter @NoArgsConstructor
public class RadiologyRequest {

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
    private String status = "EN_ATTENTE"; // EN_ATTENTE, EN_COURS, VALIDE, LIVRE, ANNULE

    @Column(name = "clinical_info", columnDefinition = "TEXT")
    private String clinicalInfo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "radiologyRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<RadiologyRequestItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "radiologyRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<RadiologyImage> images = new ArrayList<>();

    @OneToOne(mappedBy = "radiologyRequest", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private RadiologyReport report;

    /** Keep both sides of the association in sync when adding an exam line. */
    public void addItem(RadiologyRequestItem item) {
        item.setRadiologyRequest(this);
        this.items.add(item);
    }

    /** Keep both sides of the association in sync when attaching an image. */
    public void addImage(RadiologyImage image) {
        image.setRadiologyRequest(this);
        this.images.add(image);
    }

    /** Attach (or replace) the report, keeping both sides in sync. */
    public void setReportObject(RadiologyReport rep) {
        if (rep != null) {
            rep.setRadiologyRequest(this);
        }
        this.report = rep;
    }
}
