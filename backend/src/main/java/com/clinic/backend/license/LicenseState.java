package com.clinic.backend.license;

import java.time.LocalDate;

/**
 * Vue calculée de l'état de licence, consommée par le guard et les vues (bandeau).
 *
 * @param status        état courant
 * @param edition       édition de la licence active (null en essai)
 * @param clinicName    nom porté par la licence (null en essai)
 * @param validUntil    fin de validité : date d'expiration de la licence, ou fin d'essai
 * @param daysRemaining jours restants avant expiration/fin d'essai (0 si déjà expiré)
 * @param blocked       vrai si les écritures doivent être bloquées (lecture seule)
 */
public record LicenseState(
        LicenseStatus status,
        String edition,
        String clinicName,
        LocalDate validUntil,
        long daysRemaining,
        boolean blocked
) {

    public static LicenseState disabled() {
        return new LicenseState(LicenseStatus.DISABLED, null, null, null, 0, false);
    }

    /** Bandeau à afficher tant que l'état mérite un avertissement à l'utilisateur. */
    public boolean showBanner() {
        return switch (status) {
            case EXPIRED -> true;
            case TRIAL -> true;                       // toujours rappeler qu'on est en essai
            case ACTIVE -> daysRemaining <= 30;       // pré-avertissement de renouvellement
            case DISABLED -> false;
        };
    }

    /** Niveau visuel du bandeau : "danger" (bloqué), "warn" (échéance proche), "info". */
    public String bannerLevel() {
        if (status == LicenseStatus.EXPIRED) return "danger";
        if (daysRemaining <= 7) return "warn";
        return "info";
    }
}
