package com.clinic.backend.insurance;

import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "insurance_providers", uniqueConstraints = @UniqueConstraint(
        name = "uq_insurance_providers_clinic_code", columnNames = {"clinic_id", "code"}))
@Getter @Setter @NoArgsConstructor
public class InsuranceProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Discriminant multi-tenant (P4.2) — rempli par Hibernate à l'insert depuis le tenant courant. */
    @TenantId
    @Column(name = "clinic_id")
    private Long clinicId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 30)
    private String code;

    @Column(length = 30)
    private String type; // PUBLIQUE, PRIVEE, MUTUELLE

    @Column(name = "coverage_percent", precision = 5, scale = 2)
    private BigDecimal coveragePercent;

    @Column(length = 255)
    private String contact;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
