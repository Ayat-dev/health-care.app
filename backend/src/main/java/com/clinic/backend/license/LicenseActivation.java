package com.clinic.backend.license;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * État de licence de l'installation (ligne unique en pratique).
 * <p>
 * Volontairement <b>non cloisonnée par clinique</b> (pas de {@code @TenantId}) : la licence
 * gouverne le poste entier. La clé de licence signée est conservée telle quelle ; l'essai
 * est amorcé au premier démarrage. Voir {@link LicenseService}.
 */
@Entity
@Table(name = "license_activation")
@Getter
@Setter
public class LicenseActivation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "license_token", columnDefinition = "text")
    private String licenseToken;

    @Column(name = "edition")
    private String edition;

    @Column(name = "clinic_name")
    private String clinicName;

    @Column(name = "expires_on")
    private LocalDate expiresOn;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Column(name = "trial_started_at")
    private LocalDateTime trialStartedAt;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
