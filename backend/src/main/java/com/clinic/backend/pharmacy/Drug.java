package com.clinic.backend.pharmacy;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A medication in the pharmacy catalogue (commercial name + DCI/generic). Physical
 * stock lives in {@link StockItem} (one drug → many batches); prescription lines and
 * dispensations reference a Drug.
 */
@Entity
@Table(name = "drugs")
@Getter @Setter @NoArgsConstructor
public class Drug {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 30)
    private String code;                 // code interne ou DCI

    @Column(nullable = false, length = 150)
    private String name;                 // nom commercial

    @Column(name = "generic_name", length = 150)
    private String genericName;          // DCI

    @Column(length = 50)
    private String category;             // ANTIBIOTIQUE, ANALGESIQUE…

    @Column(length = 30)
    private String form;                 // COMPRIME, SIROP, INJECTABLE…

    @Column(name = "dosage_strength", length = 50)
    private String dosageStrength;       // ex: 500mg, 250mg/5ml

    @Column(nullable = false, length = 20)
    private String unit;                 // COMPRIME, ML, FLACON…

    @Column(name = "requires_prescription", nullable = false)
    private boolean requiresPrescription = true;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
