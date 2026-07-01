package com.clinic.backend.security.mfa;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Un code de secours MFA (Tier E3) — haché (BCrypt), à usage unique. Permet de se
 * connecter si l'appareil TOTP est perdu. {@code usedAt} non-null = code déjà consommé.
 */
@Entity
@Table(name = "mfa_recovery_codes")
@Getter @Setter @NoArgsConstructor
public class MfaRecoveryCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public MfaRecoveryCode(Long userId, String codeHash) {
        this.userId = userId;
        this.codeHash = codeHash;
    }
}
