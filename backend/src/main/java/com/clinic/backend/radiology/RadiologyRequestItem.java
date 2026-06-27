package com.clinic.backend.radiology;

import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One exam line within a {@link RadiologyRequest}, pointing at a {@link RadiologyExamCatalog}
 * entry. The narrative result lives on the request-level {@link RadiologyReport}.
 */
@Entity
@Table(name = "radiology_request_items")
@Getter @Setter @NoArgsConstructor
public class RadiologyRequestItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Discriminant multi-tenant (P4.2) — rempli par Hibernate à l'insert depuis le tenant courant. */
    @TenantId
    @Column(name = "clinic_id")
    private Long clinicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "radiology_request_id", nullable = false)
    private RadiologyRequest radiologyRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exam_id", nullable = false)
    private RadiologyExamCatalog exam;
}
