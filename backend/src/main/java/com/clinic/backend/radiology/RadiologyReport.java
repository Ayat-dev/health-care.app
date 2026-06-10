package com.clinic.backend.radiology;

import com.clinic.backend.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * The radiologist's compte-rendu for a {@link RadiologyRequest}: the findings and conclusion
 * narrative, who wrote it, and the sign-off once validated. One report per request.
 * The radiology analogue of {@link com.clinic.backend.lab.LabResult}.
 */
@Entity
@Table(name = "radiology_reports")
@Getter @Setter @NoArgsConstructor
public class RadiologyReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "radiology_request_id", nullable = false, unique = true)
    private RadiologyRequest radiologyRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "radiologist_id")
    private User radiologist;

    @Column(columnDefinition = "TEXT")
    private String findings;

    @Column(columnDefinition = "TEXT")
    private String conclusion;

    @Column(name = "validated_at")
    private LocalDateTime validatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validated_by")
    private User validatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
