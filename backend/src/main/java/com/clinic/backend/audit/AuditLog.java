package com.clinic.backend.audit;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entrée immuable du journal d'audit. Écrite uniquement par {@link AuditService}
 * (via {@link AuditAspect}) — jamais modifiée ni supprimée par l'application.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private String username;
    private String action;
    private String entityType;
    private Long entityId;
    private String ipAddress;
    private String userAgent;

    @Column(columnDefinition = "text")
    private String details;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    public AuditLog() {}

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public Long getEntityId() { return entityId; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public String getDetails() { return details; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setUserId(Long userId) { this.userId = userId; }
    public void setUsername(String username) { this.username = username; }
    public void setAction(String action) { this.action = action; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public void setDetails(String details) { this.details = details; }
}
