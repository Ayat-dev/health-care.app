package com.clinic.backend.repository;

import com.clinic.backend.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Jetons encore actifs d'un utilisateur (révocation en masse : logout-all / désactivation). */
    List<RefreshToken> findByUserIdAndRevokedAtIsNull(Long userId);

    /**
     * D1b — purge des jetons hors service depuis avant {@code cutoff} : soit expirés,
     * soit révoqués. On garde un jeton révoqué tant qu'il pourrait encore être présenté
     * (détection de réutilisation/vol) ; au-delà du {@code cutoff} il n'apporte plus rien.
     *
     * @return nombre de lignes supprimées
     */
    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :cutoff "
            + "OR (t.revokedAt IS NOT NULL AND t.revokedAt < :cutoff)")
    int deleteExpiredOrRevokedBefore(@Param("cutoff") LocalDateTime cutoff);
}
