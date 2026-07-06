package com.clinic.backend.license;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.util.List;

/**
 * Contenu (charge utile) d'une clé de licence, signé par l'éditeur.
 * <p>
 * Sérialisé en JSON compact puis signé en Ed25519 — voir {@link LicenseCodec}. Vérifié
 * <b>hors-ligne</b> avec la seule clé publique embarquée : impossible à forger sans la clé
 * privée de l'éditeur (jamais distribuée). Champs volontairement minimaux et stables.
 *
 * @param id       identifiant unique de la licence (traçabilité, révocation future)
 * @param clinic   nom de la clinique destinataire (affichage / rattachement)
 * @param edition  édition commerciale (ex. STANDARD, PRO)
 * @param features drapeaux de fonctionnalités optionnels
 * @param maxUsers plafond d'utilisateurs (informatif à ce stade)
 * @param issued   date d'émission
 * @param expires  date d'expiration (au-delà : licence expirée → écritures bloquées)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record License(
        String id,
        String clinic,
        String edition,
        List<String> features,
        Integer maxUsers,
        LocalDate issued,
        LocalDate expires
) {
    public boolean isExpiredOn(LocalDate day) {
        return expires != null && day.isAfter(expires);
    }
}
