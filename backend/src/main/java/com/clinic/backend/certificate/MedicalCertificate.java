package com.clinic.backend.certificate;

import com.clinic.backend.consultation.Consultation;
import com.clinic.backend.model.User;
import com.clinic.backend.patient.Patient;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Certificat médical émis par un médecin (Tier E1) : arrêt de travail, repos, aptitude,
 * présence, bonne santé, grossesse, ou général.
 * <p>
 * <b>Confidentialité</b> : le corps ({@link #content}) est saisi par le médecin ; le diagnostic
 * de la consultation n'est jamais injecté automatiquement (un arrêt de travail ne doit pas exposer
 * la pathologie). Les dates de repos ({@link #restStartDate}/{@link #restEndDate}/{@link #restDays})
 * ne concernent que les types « arrêt de travail » / « repos médical ».
 */
@Entity
@Table(name = "medical_certificates")
@Getter @Setter @NoArgsConstructor
public class MedicalCertificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Discriminant multi-tenant (P4.2) — renseigné par Hibernate via le resolver de tenant. */
    @TenantId
    @Column(name = "clinic_id")
    private Long clinicId;

    @Column(name = "certificate_number", nullable = false, unique = true, length = 25)
    private String certificateNumber;

    /** Type de certificat (code contrôlé : GENERAL, ARRET_TRAVAIL, REPOS, APTITUDE…). */
    @Column(nullable = false, length = 30)
    private String type;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private User doctor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consultation_id")
    private Consultation consultation;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate = LocalDate.now();

    // Repos prescrit (arrêt de travail / repos médical) — optionnels.
    @Column(name = "rest_start_date")
    private LocalDate restStartDate;

    @Column(name = "rest_end_date")
    private LocalDate restEndDate;

    @Column(name = "rest_days")
    private Integer restDays;

    /** Corps du certificat, rédigé par le médecin (aucun diagnostic auto). */
    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
