package com.clinic.backend.license;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.PublicKey;
import java.time.LocalDate;

/**
 * Point d'entrée de la licence : expose l'{@link LicenseState état courant} (pour le
 * guard et le bandeau) et l'activation d'une clé.
 * <p>
 * <b>Enforcement opt-in</b> : {@code app.license.enforce} (défaut {@code false}) — dev et
 * tests ne sont donc jamais bloqués et n'écrivent aucun marqueur. Activé en mode desktop.
 * <p>
 * Le calcul réel (base + essai) est délégué à {@link LicenseCalculator} ({@code @Transactional}),
 * et mis en cache quelques secondes : l'état ne change qu'au fil des jours, inutile de
 * frapper la base à chaque requête (d'autant que le DataSource desktop n'est pas poolé).
 */
@Service
@Slf4j
public class LicenseService {

    private static final long CACHE_TTL_MS = 60_000;

    private final LicenseCalculator calculator;
    private final boolean enforce;
    private final String publicKeyBase64;
    private final int trialDays;

    private volatile PublicKey publicKey;
    private volatile boolean publicKeyResolved;
    private volatile LicenseState cachedState;
    private volatile long cacheExpiryMs;

    public LicenseService(LicenseCalculator calculator,
                          @Value("${app.license.enforce:false}") boolean enforce,
                          @Value("${app.license.public-key:}") String publicKeyBase64,
                          @Value("${app.license.trial-days:30}") int trialDays) {
        this.calculator = calculator;
        this.enforce = enforce;
        this.publicKeyBase64 = publicKeyBase64;
        this.trialDays = trialDays;
    }

    /** Enforcement actif ? (le bandeau et le guard ne font rien si false). */
    public boolean isEnforced() {
        return enforce;
    }

    /** État courant (mis en cache {@value #CACHE_TTL_MS} ms). */
    public LicenseState currentState() {
        if (!enforce) {
            return LicenseState.disabled();
        }
        long now = System.currentTimeMillis();
        LicenseState cached = cachedState;
        if (cached != null && now < cacheExpiryMs) {
            return cached;
        }
        LicenseState state = calculator.compute(trialDays, publicKeyOrNull());
        cachedState = state;
        cacheExpiryMs = now + CACHE_TTL_MS;
        return state;
    }

    /** Vrai si les écritures cliniques/facturation doivent être bloquées. */
    public boolean isWriteBlocked() {
        return enforce && currentState().blocked();
    }

    /**
     * Active une clé de licence saisie par l'administrateur.
     *
     * @throws LicenseException si la clé est illisible, non authentique, déjà expirée,
     *                          ou si la clé publique n'est pas configurée
     */
    public LicenseState activate(String token) {
        PublicKey pk = requirePublicKey();
        License license = LicenseCodec.verify(token, pk);
        if (license.isExpiredOn(LocalDate.now())) {
            throw new LicenseException("Cette licence est déjà expirée (échéance : " + license.expires() + ").");
        }
        calculator.store(token.trim().replaceAll("\\s", ""), license);
        invalidateCache();
        return currentState();
    }

    void invalidateCache() {
        cacheExpiryMs = 0;
    }

    // ── Clé publique embarquée ────────────────────────────────────────────────────

    private PublicKey publicKeyOrNull() {
        if (!publicKeyResolved) {
            synchronized (this) {
                if (!publicKeyResolved) {
                    if (publicKeyBase64 != null && !publicKeyBase64.isBlank()) {
                        try {
                            publicKey = LicenseCodec.publicKeyFromBase64(publicKeyBase64);
                        } catch (LicenseException e) {
                            log.error("Clé publique de licence invalide (app.license.public-key) : {}", e.getMessage());
                            publicKey = null;
                        }
                    }
                    publicKeyResolved = true;
                }
            }
        }
        return publicKey;
    }

    private PublicKey requirePublicKey() {
        PublicKey pk = publicKeyOrNull();
        if (pk == null) {
            throw new LicenseException("Aucune clé publique de licence configurée sur cette installation.");
        }
        return pk;
    }
}
