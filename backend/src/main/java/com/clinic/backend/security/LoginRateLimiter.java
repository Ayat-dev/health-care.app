package com.clinic.backend.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * D1d — rate-limiting par IP des tentatives de login (Bucket4j, token-bucket en mémoire).
 *
 * <p>Complète le lockout <b>par compte</b> (P1.3, {@code LoginAttemptService}) : ce dernier
 * ne protège pas d'un brute-force/DoS <b>réparti sur de nombreux comptes</b> depuis une même
 * source. Ici on plafonne le nombre de tentatives par adresse IP et par fenêtre glissante.
 *
 * <p>Volontairement plus permissif que le lockout compte : une clinique derrière un NAT
 * partage une IP publique (plusieurs secrétaires qui se connectent). Bucket en mémoire =
 * suffisant pour un déploiement mono-instance ; un cluster nécessiterait un backend partagé
 * (Redis/Hazelcast), hors périmètre.
 */
@Component
public class LoginRateLimiter {

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final long maxAttempts;
    private final Duration window;

    public LoginRateLimiter(
            @Value("${app.security.login-rate-limit.max-attempts}") long maxAttempts,
            @Value("${app.security.login-rate-limit.window-minutes}") long windowMinutes) {
        this.maxAttempts = maxAttempts;
        this.window = Duration.ofMinutes(windowMinutes);
    }

    /**
     * Tente de consommer un jeton pour cette IP.
     * @return {@code true} si la tentative est autorisée, {@code false} si la limite est atteinte.
     */
    public boolean tryAcquire(String ip) {
        String key = ip != null ? ip : "unknown";
        return buckets.computeIfAbsent(key, k -> newBucket()).tryConsume(1);
    }

    /** Durée de la fenêtre en secondes (en-tête {@code Retry-After}). */
    public long windowSeconds() {
        return window.toSeconds();
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.classic(maxAttempts, Refill.greedy(maxAttempts, window));
        return Bucket.builder().addLimit(limit).build();
    }
}
