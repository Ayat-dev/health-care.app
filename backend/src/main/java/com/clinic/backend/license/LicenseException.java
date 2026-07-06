package com.clinic.backend.license;

/**
 * Erreur de licence destinée à l'UI : jeton illisible, signature invalide, ou clé
 * publique mal configurée. Le message est affichable à l'utilisateur.
 */
public class LicenseException extends RuntimeException {
    public LicenseException(String message) {
        super(message);
    }

    public LicenseException(String message, Throwable cause) {
        super(message, cause);
    }
}
