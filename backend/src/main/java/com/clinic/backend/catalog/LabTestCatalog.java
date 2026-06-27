package com.clinic.backend.catalog;

import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A laboratory analysis offered by the clinic, with its price and usual turnaround.
 * Lab requests reference these entries.
 */
@Entity
@Table(name = "lab_test_catalog", uniqueConstraints = @UniqueConstraint(
        name = "uq_lab_test_catalog_clinic_code", columnNames = {"clinic_id", "code"}))
@Getter @Setter @NoArgsConstructor
public class LabTestCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Discriminant multi-tenant (P4.2) — rempli par Hibernate à l'insert depuis le tenant courant. */
    @TenantId
    @Column(name = "clinic_id")
    private Long clinicId;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 50)
    private String category; // HEMATOLOGIE, BIOCHIMIE, SEROLOGIE, BACTERIO…

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "turnaround_hours")
    private Integer turnaroundHours;

    @Column(name = "reference_range", columnDefinition = "TEXT")
    private String referenceRange;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
