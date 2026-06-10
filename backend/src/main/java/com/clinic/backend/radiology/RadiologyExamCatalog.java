package com.clinic.backend.radiology;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * An imaging exam offered by the clinic (radiography, ultrasound, CT, MRI, mammography),
 * with its modality type and price. Radiology requests reference these entries.
 * The radiology analogue of {@link com.clinic.backend.catalog.LabTestCatalog}.
 */
@Entity
@Table(name = "radiology_exam_catalog")
@Getter @Setter @NoArgsConstructor
public class RadiologyExamCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 30)
    private String type; // RADIOGRAPHIE, ECHOGRAPHIE, SCANNER, IRM, MAMMOGRAPHIE

    @Column(length = 100)
    private String region;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
