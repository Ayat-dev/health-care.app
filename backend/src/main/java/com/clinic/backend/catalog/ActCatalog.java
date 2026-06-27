package com.clinic.backend.catalog;

import com.clinic.backend.department.Department;
import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Billable act with a default price (e.g. a consultation, a dressing). Invoice items
 * reference these to pre-fill descriptions and prices.
 */
@Entity
@Table(name = "act_catalog", uniqueConstraints = @UniqueConstraint(
        name = "uq_act_catalog_clinic_code", columnNames = {"clinic_id", "code"}))
@Getter @Setter @NoArgsConstructor
public class ActCatalog {

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
