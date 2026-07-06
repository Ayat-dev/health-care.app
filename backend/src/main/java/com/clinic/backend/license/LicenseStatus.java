package com.clinic.backend.license;

/**
 * État courant de la licence de l'installation.
 */
public enum LicenseStatus {
    /** Application non soumise à licence (enforcement désactivé — dev/test). */
    DISABLED,
    /** Licence valide et non expirée. */
    ACTIVE,
    /** Pas de licence valide mais période d'essai en cours. */
    TRIAL,
    /** Essai terminé ou licence expirée → écritures bloquées (lecture seule). */
    EXPIRED
}
