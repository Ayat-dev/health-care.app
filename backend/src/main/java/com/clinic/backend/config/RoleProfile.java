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

    /**
     * Tenant transverse (multi-tenant P4.2) : ne voit QUE le registre des cliniques.
     * N'accède à aucune donnée clinique (qui est tenant-scopée et lui serait vide).
     */
    SUPER_ADMIN(
        "/admin/clinics",
        EnumSet.of(ADMIN_CLINICS),
        Set.of()
    ),

    /**
     * Propriétaire/exploitant business (P6). Cockpit financier + catalogues commerciaux
     * + stats agrégées. <b>Aucun PHI</b> (même mur de confidentialité que l'ADMIN) :
     * pas de patients, consultations, labo, imagerie, maternité, hospitalisation nominatifs.
     * Atterrit sur le tableau de bord financier ({@code /reports}).
     */
    OWNER(
        "/reports",
        EnumSet.of(NOTIFICATIONS, BILLING, REPORTS,
                   ADMIN_DEPTS, ADMIN_INSURANCE, ADMIN_ACTS),
        Set.of("FACTURE_IMPAYEE", "STOCK_ALERTE", "SYSTEM")
    ),

    /**
     * Exploitant <b>technique</b> (P6) : accès, configuration système, journal d'audit,
     * référentiels techniques (CIM-10, catalogue d'analyses), notifications système.
     * <b>Ni PHI ni finances</b> — set explicite (plus de {@code complementOf}, qui lui
     * donnait clinique + finances). Le business est désormais au {@link #OWNER}.
     */
    ADMIN(
        "/admin/users",
        EnumSet.of(NOTIFICATIONS,
                   ADMIN_USERS, ADMIN_LAB_TESTS, ADMIN_ICD10, ADMIN_AUDIT, ADMIN_CONFIG),
        Set.of("SYSTEM")
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
        // Pas de DASHBOARD : le tableau de bord KPI est réservé à ADMIN/MEDECIN.
        EnumSet.of(PATIENTS, APPOINTMENTS, NOTIFICATIONS,
                   CONSULTATIONS, MATERNITY, HOSPITALIZATION),
        Set.of("RAPPEL_RDV")
    ),

    SECRETAIRE(
        "/appointments",
        // Pas de DASHBOARD : le tableau de bord KPI est réservé à ADMIN/MEDECIN.
        EnumSet.of(PATIENTS, APPOINTMENTS, NOTIFICATIONS,
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
        "/billing/queue", // la file d'attente caisse, pas le tableau de bord (P5.1)
        EnumSet.of(BILLING, REPORTS, NOTIFICATIONS),
        Set.of("FACTURE_IMPAYEE")
    ),

    /**
     * Patient (portail). Aucun module de la sidebar staff : le portail {@code /portal/**}
     * a sa propre mise en page. Atterrit sur {@code /portal} après login.
     */
    PATIENT(
        "/portal",
        EnumSet.noneOf(Module.class),
        Set.of()
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
