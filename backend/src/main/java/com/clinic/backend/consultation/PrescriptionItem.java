package com.clinic.backend.consultation;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "prescription_items")
@Getter @Setter @NoArgsConstructor
public class PrescriptionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prescription_id", nullable = false)
    private Prescription prescription;

    // drug_id FK→drugs(id) wired with the Pharmacy module; free text for now.
    @Column(name = "drug_id")
    private Long drugId;

    @Column(name = "drug_name", nullable = false, length = 150)
    private String drugName;

    @Column(nullable = false, length = 100)
    private String dosage;

    @Column(nullable = false, length = 100)
    private String frequency;

    @Column(length = 100)
    private String duration;

    private Integer quantity;

    @Column(columnDefinition = "TEXT")
    private String instructions;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
