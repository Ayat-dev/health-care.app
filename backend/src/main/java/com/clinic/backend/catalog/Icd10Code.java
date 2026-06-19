package com.clinic.backend.catalog;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * An ICD-10 (CIM-10) diagnosis code from the reference classification.
 * <p>
 * Consultations keep a free-text {@code diagnosis} for narrative, plus a
 * comma-separated {@code icd10_codes} string of codes picked from this catalog
 * (auto-completion in the consultation form). Coded diagnoses make epidemiology
 * (top pathologies) and act-based billing reliable — the free text stays as a
 * complement, never replaced.
 */
@Entity
@Table(name = "icd10_catalog")
@Getter @Setter @NoArgsConstructor
public class Icd10Code {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Code CIM-10, ex. {@code J06.9}, {@code I10}, {@code O80}. */
    @Column(nullable = false, unique = true, length = 10)
    private String code;

    /** Libellé français, ex. « Infection aiguë des voies respiratoires supérieures, sans précision ». */
    @Column(nullable = false, length = 255)
    private String title;

    /** Chapitre / catégorie, ex. « Maladies de l'appareil respiratoire ». */
    @Column(length = 120)
    private String category;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
