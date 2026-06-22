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
    DASHBOARD       ("/dashboard",        "Tableau de bord", "⊞",  "nav.dashboard",        Section.PRINCIPAL),
    PATIENTS        ("/patients",         "Patients",        "👤", "nav.patients",         Section.PRINCIPAL),
    APPOINTMENTS    ("/appointments",     "Rendez-vous",     "📅", "nav.appointments",     Section.PRINCIPAL),
    NOTIFICATIONS   ("/notifications",    "Notifications",   "🔔", "nav.notifications",    Section.PRINCIPAL),

    // ── SOINS ────────────────────────────────────────────────────────────────────
    CONSULTATIONS   ("/consultations",    "Consultations",   "🩺", "nav.consultations",    Section.SOINS),
    PHARMACY        ("/pharmacy",         "Pharmacie",       "💊", "nav.pharmacy",         Section.SOINS),
    LAB             ("/lab",              "Laboratoire",     "🔬", "nav.lab",              Section.SOINS),
    RADIOLOGY       ("/radiology",        "Imagerie",        "🩻", "nav.radiology",        Section.SOINS),
    MATERNITY       ("/maternity",        "Maternité",       "🤰", "nav.maternity",        Section.SOINS),

    // ── GESTION ──────────────────────────────────────────────────────────────────
    BILLING         ("/billing",          "Facturation",     "💳", "nav.billing",          Section.GESTION),
    HOSPITALIZATION ("/hospitalization",  "Hospitalisation", "🏥", "nav.hospitalization",  Section.GESTION),
    REPORTS         ("/reports",          "Rapports",        "📊", "nav.reports",          Section.GESTION),

    // ── ADMIN ─────────────────────────────────────────────────────────────────────
    // Transverse (SUPER_ADMIN uniquement) — registre des cliniques / tenants (P4.2).
    ADMIN_CLINICS   ("/admin/clinics",    "Cliniques",       "🏛", "nav.admin_clinics",    Section.ADMIN),
    ADMIN_USERS     ("/admin/users",      "Utilisateurs",    "⚙",  "nav.admin_users",     Section.ADMIN),
    ADMIN_DEPTS     ("/admin/departments","Départements",    "🏢", "nav.admin_depts",      Section.ADMIN),
    ADMIN_INSURANCE ("/admin/insurance",  "Assureurs",       "🛡️", "nav.admin_insurance",  Section.ADMIN),
    ADMIN_ACTS      ("/admin/acts",       "Actes & tarifs",  "🧾", "nav.admin_acts",       Section.ADMIN),
    ADMIN_LAB_TESTS ("/admin/lab-tests",  "Analyses",        "🔬", "nav.admin_lab_tests",  Section.ADMIN),
    ADMIN_ICD10     ("/admin/icd10",      "Diagnostics CIM-10", "🏷️", "nav.admin_icd10",   Section.ADMIN),
    ADMIN_AUDIT     ("/admin/audit",      "Journal d'audit", "📜", "nav.admin_audit",      Section.ADMIN),
    ADMIN_CONFIG    ("/admin/config",     "Configuration",   "⚙️", "nav.admin_config",     Section.ADMIN);

    // ─────────────────────────────────────────────────────────────────────────────

    public enum Section { PRINCIPAL, SOINS, GESTION, ADMIN }

    public final String urlPrefix;
    /** Libellé français en dur — repli si la clé i18n {@link #labelKey} est absente. */
    public final String label;
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
