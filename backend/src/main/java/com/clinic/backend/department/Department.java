package com.clinic.backend.department;

import jakarta.persistence.*;
import org.hibernate.annotations.TenantId;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "departments", uniqueConstraints = @UniqueConstraint(
        name = "uq_departments_clinic_code", columnNames = {"clinic_id", "code"}))
@Getter @Setter @NoArgsConstructor
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Discriminant multi-tenant (P4.2) — rempli par Hibernate à l'insert depuis le tenant courant. */
    @TenantId
    @Column(name = "clinic_id")
    private Long clinicId;

    @Column(nullable = false, length = 20)
    private String code; // MED_GEN, MATERNITE, DENTAIRE…

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 7)
    private String color; // hex pour l'UI (#2563eb)

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
