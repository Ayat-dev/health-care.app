package com.clinic.backend.license;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.PublicKey;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Calcul transactionnel de l'état de licence (accès base + croisement des marqueurs
 * d'essai). Séparé de {@link LicenseService} — qui porte le cache et le drapeau
 * d'enforcement — pour que les méthodes {@code @Transactional} soient toujours appelées
 * via le proxy Spring (jamais en auto-invocation), et pour n'ouvrir de transaction
 * qu'en cas de recalcul réel (le DataSource desktop n'est pas poolé).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LicenseCalculator {

    /** Recul d'horloge toléré avant de considérer l'installation suspecte. */
    private static final long CLOCK_REWIND_TOLERANCE_DAYS = 1;
    /** Throttle d'écriture de last_seen_at (évite un write par recalcul). */
    private static final long LAST_SEEN_WRITE_THROTTLE_MINUTES = 5;

    private final LicenseActivationRepository repository;
    private final TrialStore trialStore;

    @Transactional
    public LicenseState compute(int trialDays, PublicKey publicKeyOrNull) {
        LicenseActivation row = repository.findFirstByOrderByIdAsc().orElseGet(LicenseActivation::new);

        LocalDateTime effectiveStart = resolveTrialStart(row);
        boolean clockRewind = detectClockRewind(row);
        repository.save(row);

        LocalDate today = LocalDate.now();

        // 1) Licence activée : prioritaire sur l'essai.
        if (row.getLicenseToken() != null && publicKeyOrNull != null) {
            try {
                License lic = LicenseCodec.verify(row.getLicenseToken(), publicKeyOrNull);
                if (lic.isExpiredOn(today)) {
                    return new LicenseState(LicenseStatus.EXPIRED, lic.edition(), lic.clinic(),
                            lic.expires(), 0, true);
                }
                long days = ChronoUnit.DAYS.between(today, lic.expires());
                return new LicenseState(LicenseStatus.ACTIVE, lic.edition(), lic.clinic(),
                        lic.expires(), days, false);
            } catch (LicenseException e) {
                log.warn("Licence stockée invalide, retour au régime d'essai : {}", e.getMessage());
            }
        }

        // 2) Essai. Un recul d'horloge suspect → traité comme expiré.
        LocalDate trialEnd = effectiveStart.toLocalDate().plusDays(trialDays);
        if (clockRewind) {
            log.warn("Recul d'horloge détecté → mode lecture seule (essai considéré terminé).");
            return new LicenseState(LicenseStatus.EXPIRED, null, null, trialEnd, 0, true);
        }
        if (!today.isAfter(trialEnd)) {
            long days = ChronoUnit.DAYS.between(today, trialEnd);
            return new LicenseState(LicenseStatus.TRIAL, null, null, trialEnd, Math.max(0, days), false);
        }
        return new LicenseState(LicenseStatus.EXPIRED, null, null, trialEnd, 0, true);
    }

    @Transactional
    public void store(String token, License license) {
        LicenseActivation row = repository.findFirstByOrderByIdAsc().orElseGet(LicenseActivation::new);
        row.setLicenseToken(token);
        row.setEdition(license.edition());
        row.setClinicName(license.clinic());
        row.setExpiresOn(license.expires());
        row.setActivatedAt(LocalDateTime.now());
        repository.save(row);
        log.info("Licence activée : édition {}, clinique « {} », valide jusqu'au {}.",
                license.edition(), license.clinic(), license.expires());
    }

    // ── Essai : croise base + fichier + registre, retient la date la plus ancienne ──

    private LocalDateTime resolveTrialStart(LicenseActivation row) {
        List<LocalDateTime> found = new ArrayList<>();
        Optional.ofNullable(row.getTrialStartedAt()).ifPresent(found::add);
        trialStore.readFile().ifPresent(found::add);
        trialStore.readRegistry().ifPresent(found::add);

        LocalDateTime start;
        if (found.isEmpty()) {
            start = LocalDateTime.now();
            log.info("Première exécution sous licence : démarrage de la période d'essai le {}.", start.toLocalDate());
        } else {
            start = found.stream().min(LocalDateTime::compareTo).orElseThrow();
        }

        // Réaligne toutes les sources sur la date la plus ancienne (back-fill anti-triche).
        if (row.getTrialStartedAt() == null || row.getTrialStartedAt().isAfter(start)) {
            row.setTrialStartedAt(start);
        }
        trialStore.writeFile(start);
        trialStore.writeRegistry(start);
        return start;
    }

    private boolean detectClockRewind(LicenseActivation row) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastSeen = row.getLastSeenAt();
        boolean rewind = lastSeen != null && now.isBefore(lastSeen.minusDays(CLOCK_REWIND_TOLERANCE_DAYS));
        // last_seen_at ne fait qu'avancer (max), et on n'écrit qu'au-delà du throttle.
        if (lastSeen == null || now.isAfter(lastSeen.plusMinutes(LAST_SEEN_WRITE_THROTTLE_MINUTES))) {
            row.setLastSeenAt(lastSeen == null || now.isAfter(lastSeen) ? now : lastSeen);
        }
        return rewind;
    }
}
