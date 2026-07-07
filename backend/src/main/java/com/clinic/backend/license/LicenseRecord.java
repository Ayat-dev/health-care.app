package com.clinic.backend.license;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;

/**
 * Trace d'une licence émise, conservée par l'éditeur (registre local) pour le suivi
 * commercial : renouvellements, support, réémission. N'est jamais distribuée au client.
 *
 * @param id       identifiant de la licence (repris du contenu signé)
 * @param clinic   clinique destinataire
 * @param edition  édition vendue
 * @param maxUsers plafond d'utilisateurs (le cas échéant)
 * @param issued   date d'émission
 * @param expires  date d'expiration
 * @param token    la clé signée effectivement remise au client
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LicenseRecord(
        String id,
        String clinic,
        String edition,
        Integer maxUsers,
        LocalDate issued,
        LocalDate expires,
        String token
) {
    public static LicenseRecord of(License license, String token) {
        return new LicenseRecord(license.id(), license.clinic(), license.edition(),
                license.maxUsers(), license.issued(), license.expires(), token);
    }
}
