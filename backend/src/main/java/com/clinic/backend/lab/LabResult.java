package com.clinic.backend.lab;

import com.clinic.backend.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * The measured value for a single {@link LabRequestItem}: what the laborantin entered,
 * whether it falls outside the reference range, and the validating clinician once signed off.
 */
@Entity
@Table(name = "lab_results")
@Getter @Setter @NoArgsConstructor
public class LabResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lab_request_item_id", nullable = false, unique = true)
    private LabRequestItem requestItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "laborantin_id")
    private User laborantin;

    @Column(name = "result_value", columnDefinition = "TEXT", nullable = false)
    private String resultValue;

    @Column(length = 30)
    private String unit;

    @Column(name = "reference_range", columnDefinition = "TEXT")
    private String referenceRange;

    @Column(name = "is_abnormal", nullable = false)
    private boolean abnormal = false;

    @Column(name = "validated_at")
    private LocalDateTime validatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validated_by")
    private User validatedBy;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
