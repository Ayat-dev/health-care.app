package com.clinic.backend.security.mfa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface MfaRecoveryCodeRepository extends JpaRepository<MfaRecoveryCode, Long> {

    /** Codes de secours non encore consommés d'un utilisateur. */
    List<MfaRecoveryCode> findByUserIdAndUsedAtIsNull(Long userId);

    long countByUserIdAndUsedAtIsNull(Long userId);

    @Transactional
    void deleteByUserId(Long userId);
}
