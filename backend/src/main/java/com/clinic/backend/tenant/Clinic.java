package com.clinic.backend.tenant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Registre des tenants (P4.2) : une clinique = un locataire. Référencée par {@code clinic_id}
 * (discriminant {@code @TenantId}) sur les tables cliniques. <b>N'est pas elle-même
 * tenant-scopée</b> — le SUPER_ADMIN voit/administre toutes les cliniques.
 */
@Entity
@Table(name = "clinics")
@Getter @Setter @NoArgsConstructor
public class Clinic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(length = 30)
    private String phone;

    @Column(length = 120)
    private String email;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    void onUpdate() { this.updatedAt = LocalDateTime.now(); }

    public Clinic(String code, String name) {
        this.code = code;
        this.name = name;
    }
}
