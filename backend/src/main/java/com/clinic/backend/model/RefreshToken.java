package com.clinic.backend.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Refresh token persistant (P4.4). Seule la valeur HACHÉE (SHA-256 hex) est stockée :
 * une fuite de base ne donne pas de jeton réutilisable. Rotation : chaque usage révoque
 * l'ancien et en crée un nouveau ({@link #replacedById}) ; la réutilisation d'un jeton
 * déjà révoqué trahit un vol → révocation de toute la lignée.
 *
 * <p>Pas de {@code @TenantId} (comme {@code users}) : l'auth est globale, keyée par
 * {@link #userId} qui résout la clinique.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "replaced_by_id")
    private Long replacedById;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public RefreshToken() {}

    public RefreshToken(Long userId, String tokenHash, LocalDateTime expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    /** Utilisable = non révoqué ET non expiré. */
    public boolean isActive() {
        return revokedAt == null && expiresAt.isAfter(LocalDateTime.now());
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getTokenHash() { return tokenHash; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(LocalDateTime revokedAt) { this.revokedAt = revokedAt; }
    public Long getReplacedById() { return replacedById; }
    public void setReplacedById(Long replacedById) { this.replacedById = replacedById; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
