package com.clinic.backend.security;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * D1b — nettoyage périodique des refresh tokens hors service (expirés/révoqués anciens).
 *
 * <p>Tâche de fond <b>tenant-agnostique</b> : les refresh tokens ne portent pas
 * {@code @TenantId} (auth globale keyée par {@code user_id}), donc — contrairement aux
 * schedulers métier (cf. {@code StockAlertService}) — pas d'itération {@code runAs} par
 * clinique. La logique de purge (calcul du cutoff + suppression) vit dans
 * {@link RefreshTokenService#purgeStaleTokens()}, testable hors planification.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupScheduler {

    private final RefreshTokenService refreshTokenService;

    /** Chaque jour à 03:30 (heure creuse) — purge les jetons expirés/révoqués anciens. */
    @Scheduled(cron = "0 30 3 * * *")
    public void purge() {
        refreshTokenService.purgeStaleTokens();
    }
}
