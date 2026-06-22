package com.clinic.backend.repository;

import com.clinic.backend.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Jetons encore actifs d'un utilisateur (révocation en masse : logout-all / désactivation). */
    List<RefreshToken> findByUserIdAndRevokedAtIsNull(Long userId);
}
