package com.clinic.backend.security;

import com.clinic.backend.model.RefreshToken;
import com.clinic.backend.repository.RefreshTokenRepository;
import com.clinic.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D1b — purge planifiée des refresh tokens hors service. Rétention = 7 jours (défaut profil
 * test) : un jeton expiré OU révoqué depuis plus de 7 jours est supprimé ; un jeton encore
 * actif, ou tout juste expiré/révoqué (dans la fenêtre de rétention), est conservé.
 */
@SpringBootTest
@ActiveProfiles("test")
class RefreshTokenCleanupTest {

    @Autowired RefreshTokenRepository repo;
    @Autowired RefreshTokenService service;
    @Autowired UserRepository userRepository;

    private Long save(String hash, LocalDateTime expiresAt, LocalDateTime revokedAt) {
        Long userId = userRepository.findByUsername("admin").orElseThrow().getId();
        RefreshToken t = new RefreshToken(userId, hash, expiresAt);
        if (revokedAt != null) t.setRevokedAt(revokedAt);
        return repo.save(t).getId();
    }

    @Test
    void purge_supprime_les_jetons_expires_ou_revoques_anciens_et_garde_les_autres() {
        LocalDateTime now = LocalDateTime.now();

        // Hors service depuis > 7 jours → à purger
        Long expiredOld = save("d1b-expired-old", now.minusDays(10), null);
        Long revokedOld = save("d1b-revoked-old", now.plusDays(5), now.minusDays(10));

        // Encore pertinents → à garder
        Long active        = save("d1b-active",         now.plusDays(5), null);
        Long expiredRecent = save("d1b-expired-recent", now.minusDays(1), null);          // dans la rétention
        Long revokedRecent = save("d1b-revoked-recent", now.plusDays(5), now.minusDays(1)); // révoqué récemment

        int deleted = service.purgeStaleTokens();

        assertTrue(deleted >= 2, "au moins les 2 jetons anciens supprimés, reçu " + deleted);
        assertFalse(repo.existsById(expiredOld), "jeton expiré ancien doit être purgé");
        assertFalse(repo.existsById(revokedOld), "jeton révoqué ancien doit être purgé");
        assertTrue(repo.existsById(active), "jeton actif doit être conservé");
        assertTrue(repo.existsById(expiredRecent), "jeton expiré récemment (rétention) conservé");
        assertTrue(repo.existsById(revokedRecent), "jeton révoqué récemment (réutilisation) conservé");
    }
}
