package com.clinic.backend.config;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static com.clinic.backend.config.Module.*;

/**
 * Source de vérité unique pour les règles métier par rôle.
 * <p>
 * Chaque entrée déclare :
 * <ul>
 *   <li>{@code homepage}           – page de redirection après login</li>
 *   <li>{@code modules}            – modules visibles dans la sidebar</li>
 *   <li>{@code notificationTypes}  – types de notifications in-app pertinents</li>
 * </ul>
 *
 * Cette enum pilote :
 * <ol>
 *   <li>Le redirect post-login ({@code RoleAuthenticationSuccessHandler})</li>
 *   <li>La sidebar ({@code GlobalModelAdvice} → {@code base.html})</li>
 *   <li>Le badge et l'inbox des notifications ({@code NotificationService})</li>
 * </ol>
 *
 * Ajouter un rôle = une ligne ici. Tout le reste s'adapte automatiquement.
 */
public enum RoleProfile {

    ADMIN(
        "/dashboard",
        EnumSet.allOf(Module.class),
        Set.of("RAPPEL_RDV", "RESULTAT_LABO", "STOCK_ALERTE", "FACTURE_IMPAYEE", "SYSTEM")
    ),

    MEDECIN(
        "/appointments",
        EnumSet.of(DASHBOARD, PATIENTS, APPOINTMENTS, NOTIFICATIONS,
                   CONSULTATIONS, LAB, RADIOLOGY, MATERNITY,
                   HOSPITALIZATION, REPORTS),
        Set.of("RAPPEL_RDV", "RESULTAT_LABO")
    ),

    INFIRMIER(
        "/appointments",
        EnumSet.of(DASHBOARD, PATIENTS, APPOINTMENTS, NOTIFICATIONS,
                   CONSULTATIONS, MATERNITY, HOSPITALIZATION),
        Set.of("RAPPEL_RDV")
    ),

    SECRETAIRE(
        "/appointments",
        EnumSet.of(DASHBOARD, PATIENTS, APPOINTMENTS, NOTIFICATIONS,
                   BILLING, REPORTS),
        Set.of("RAPPEL_RDV", "FACTURE_IMPAYEE")
    ),

    PHARMACIEN(
        "/pharmacy",
        EnumSet.of(PHARMACY, NOTIFICATIONS),
        Set.of("STOCK_ALERTE")
    ),

    LABORANTIN(
        "/lab",
        EnumSet.of(LAB, NOTIFICATIONS),
        Set.of("RESULTAT_LABO")
    ),

    CAISSIER(
        "/billing",
        EnumSet.of(BILLING, REPORTS, NOTIFICATIONS),
        Set.of("FACTURE_IMPAYEE")
    );

    // ─────────────────────────────────────────────────────────────────────────────

    public final String homepage;
    public final Set<Module> modules;
    public final Set<String> notificationTypes;

    RoleProfile(String homepage, Set<Module> modules, Set<String> notificationTypes) {
        this.homepage          = homepage;
        this.modules           = modules;
        this.notificationTypes = notificationTypes;
    }

    /** Modules visibles triés dans l'ordre de déclaration de {@link Module}. */
    public List<Module> orderedModules() {
        return Arrays.stream(Module.values())
                .filter(modules::contains)
                .toList();
    }

    /** Modules visibles d'une section donnée, dans l'ordre de déclaration. */
    public List<Module> modulesForSection(Module.Section section) {
        return orderedModules().stream()
                .filter(m -> m.section == section)
                .toList();
    }

    /**
     * Retourne le profil pour un rôle (chaîne DB, ex. "PHARMACIEN").
     * Renvoie {@link #MEDECIN} comme fallback sûr si le rôle est inconnu.
     */
    public static RoleProfile fromRole(String role) {
        if (role == null) return MEDECIN;
        try {
            return valueOf(role);
        } catch (IllegalArgumentException e) {
            return MEDECIN;
        }
    }
}
