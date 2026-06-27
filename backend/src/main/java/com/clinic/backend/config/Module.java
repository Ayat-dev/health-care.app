package com.clinic.backend.config;

/**
 * Catalogue de tous les modules navigables de l'application web.
 * Chaque entrée porte l'URL de base, le libellé affiché, l'icône et la section de la sidebar.
 * <p>
 * {@link RoleProfile} référence ce catalogue pour déclarer ce que chaque rôle peut voir.
 * {@code GlobalModelAdvice} l'expose par section au template Thymeleaf pour construire
 * la sidebar dynamiquement — sans aucun {@code sec:authorize} manuel.
 */
public enum Module {

    // ── PRINCIPAL ────────────────────────────────────────────────────────────────
    // NB : `icon` porte désormais un identifiant d'icône SVG (sprite fragments/icons.html),
    // pas un emoji — cf. docs/UX-GUIDELINES.md §7 (pas d'emoji comme icône structurelle).
    DASHBOARD       ("/dashboard",        "Tableau de bord", "dashboard",   "nav.dashboard",        Section.PRINCIPAL),
    PATIENTS        ("/patients",         "Patients",        "user",        "nav.patients",         Section.PRINCIPAL),
    APPOINTMENTS    ("/appointments",     "Rendez-vous",     "calendar",    "nav.appointments",     Section.PRINCIPAL),
    NOTIFICATIONS   ("/notifications",    "Notifications",   "bell",        "nav.notifications",    Section.PRINCIPAL),

    // ── SOINS ────────────────────────────────────────────────────────────────────
    CONSULTATIONS   ("/consultations",    "Consultations",   "activity",    "nav.consultations",    Section.SOINS),
    PHARMACY        ("/pharmacy",         "Pharmacie",       "pill",        "nav.pharmacy",         Section.SOINS),
    LAB             ("/lab",              "Laboratoire",     "microscope",  "nav.lab",              Section.SOINS),
    RADIOLOGY       ("/radiology",        "Imagerie",        "scan",        "nav.radiology",        Section.SOINS),
    MATERNITY       ("/maternity",        "Maternité",       "baby",        "nav.maternity",        Section.SOINS),

    // ── GESTION ──────────────────────────────────────────────────────────────────
    BILLING         ("/billing",          "Facturation",     "credit-card", "nav.billing",          Section.GESTION),
    HOSPITALIZATION ("/hospitalization",  "Hospitalisation", "bed",         "nav.hospitalization",  Section.GESTION),
    REPORTS         ("/reports",          "Rapports",        "bar-chart",   "nav.reports",          Section.GESTION),

    // ── ADMIN ─────────────────────────────────────────────────────────────────────
    // Transverse (SUPER_ADMIN uniquement) — registre des cliniques / tenants (P4.2).
    ADMIN_CLINICS   ("/admin/clinics",    "Cliniques",       "landmark",    "nav.admin_clinics",    Section.ADMIN),
    ADMIN_USERS     ("/admin/users",      "Utilisateurs",    "users",       "nav.admin_users",      Section.ADMIN),
    ADMIN_DEPTS     ("/admin/departments","Départements",    "building",    "nav.admin_depts",      Section.ADMIN),
    ADMIN_INSURANCE ("/admin/insurance",  "Assureurs",       "shield",      "nav.admin_insurance",  Section.ADMIN),
    ADMIN_ACTS      ("/admin/acts",       "Actes & tarifs",  "receipt",     "nav.admin_acts",       Section.ADMIN),
    ADMIN_LAB_TESTS ("/admin/lab-tests",  "Analyses",        "flask",       "nav.admin_lab_tests",  Section.ADMIN),
    ADMIN_ICD10     ("/admin/icd10",      "Diagnostics CIM-10", "tag",      "nav.admin_icd10",      Section.ADMIN),
    ADMIN_AUDIT     ("/admin/audit",      "Journal d'audit", "scroll",      "nav.admin_audit",      Section.ADMIN),
    ADMIN_CONFIG    ("/admin/config",     "Configuration",   "settings",    "nav.admin_config",     Section.ADMIN);

    // ─────────────────────────────────────────────────────────────────────────────

    public enum Section { PRINCIPAL, SOINS, GESTION, ADMIN }

    public final String urlPrefix;
    /** Libellé français en dur — repli si la clé i18n {@link #labelKey} est absente. */
    public final String label;
    /** Identifiant d'icône SVG (symbole `#ic-{icon}` du sprite `fragments/icons.html`). */
    public final String icon;
    /** Clé du bundle messages (P3.2) — les vues affichent {@code #{labelKey}}. */
    public final String labelKey;
    public final Section section;

    Module(String urlPrefix, String label, String icon, String labelKey, Section section) {
        this.urlPrefix = urlPrefix;
        this.label     = label;
        this.icon      = icon;
        this.labelKey  = labelKey;
        this.section   = section;
    }
}
