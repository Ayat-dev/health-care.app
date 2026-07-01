package com.clinic.backend.model;

import jakarta.persistence.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "users")
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String username;

    private String password;
    private String fullName;
    private String role;

    /**
     * Clinique d'appartenance (multi-tenant P4.2). {@code null} = compte transverse
     * (SUPER_ADMIN). C'est la source de résolution du tenant courant ({@code TenantContext}),
     * d'où l'absence de {@code @TenantId} sur {@code users} (sinon la connexion par
     * username serait filtrée avant de connaître le tenant).
     */
    @Column(name = "clinic_id")
    private Long clinicId;

    private boolean active = true;

    // Anti-brute-force (P1.3) : échecs consécutifs + verrou temporaire.
    @Column(nullable = false)
    private int failedAttempts = 0;

    private LocalDateTime lockedUntil;

    /**
     * Version de token (révocation JWT, P4.4). Embarquée comme claim {@code tv} dans
     * chaque access token ; {@code JwtFilter} rejette tout token dont le {@code tv} ne
     * correspond plus. L'incrémenter (logout-all, désactivation, vol détecté) invalide
     * INSTANTANÉMENT tous les access tokens en cours, sans attendre leur expiration.
     */
    @Column(name = "token_version", nullable = false)
    private int tokenVersion = 0;

    /**
     * MFA/2FA par TOTP (Tier E3, opt-in). {@code mfaEnabled} = second facteur exigé au login web ;
     * {@code mfaSecret} = clé TOTP (base32) chiffrée au repos ({@link com.clinic.backend.crypto.PhiStringConverter}).
     */
    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled = false;

    @Convert(converter = com.clinic.backend.crypto.PhiStringConverter.class)
    @Column(name = "mfa_secret", length = 255)
    private String mfaSecret;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime deletedAt;

    public User() {}

    public User(String username, String password, String fullName, String role) {
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.role = role;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(() -> "ROLE_" + role);
    }

    @Override
    public String getPassword() { return password; }

    @Override
    public String getUsername() { return username; }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() {
        return lockedUntil == null || lockedUntil.isBefore(LocalDateTime.now());
    }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return active; }

    public Long getId() { return id; }

    public Long getClinicId() { return clinicId; }

    public void setClinicId(Long clinicId) { this.clinicId = clinicId; }

    public String getFullName() { return fullName; }

    public String getRole() { return role; }

    public boolean isActive() { return active; }

    public int getFailedAttempts() { return failedAttempts; }

    public LocalDateTime getLockedUntil() { return lockedUntil; }

    public int getTokenVersion() { return tokenVersion; }

    public void setTokenVersion(int tokenVersion) { this.tokenVersion = tokenVersion; }

    /** Invalide tous les access tokens en cours (révocation JWT, P4.4). */
    public void bumpTokenVersion() { this.tokenVersion++; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public LocalDateTime getDeletedAt() { return deletedAt; }

    public void setUsername(String username) { this.username = username; }

    public void setPassword(String password) { this.password = password; }

    public void setFullName(String fullName) { this.fullName = fullName; }

    public void setRole(String role) { this.role = role; }

    public void setActive(boolean active) { this.active = active; }

    public void setFailedAttempts(int failedAttempts) { this.failedAttempts = failedAttempts; }

    public void setLockedUntil(LocalDateTime lockedUntil) { this.lockedUntil = lockedUntil; }

    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }

    public boolean isMfaEnabled() { return mfaEnabled; }

    public void setMfaEnabled(boolean mfaEnabled) { this.mfaEnabled = mfaEnabled; }

    public String getMfaSecret() { return mfaSecret; }

    public void setMfaSecret(String mfaSecret) { this.mfaSecret = mfaSecret; }
}
