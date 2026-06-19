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
    DASHBOARD       ("/dashboard",        "Tableau de bord", "⊞",  Section.PRINCIPAL),
    PATIENTS        ("/patients",         "Patients",        "👤", Section.PRINCIPAL),
    APPOINTMENTS    ("/appointments",     "Rendez-vous",     "📅", Section.PRINCIPAL),
    NOTIFICATIONS   ("/notifications",    "Notifications",   "🔔", Section.PRINCIPAL),

    // ── SOINS ────────────────────────────────────────────────────────────────────
    CONSULTATIONS   ("/consultations",    "Consultations",   "🩺", Section.SOINS),
    PHARMACY        ("/pharmacy",         "Pharmacie",       "💊", Section.SOINS),
    LAB             ("/lab",              "Laboratoire",     "🔬", Section.SOINS),
    RADIOLOGY       ("/radiology",        "Imagerie",        "🩻", Section.SOINS),
    MATERNITY       ("/maternity",        "Maternité",       "🤰", Section.SOINS),

    // ── GESTION ──────────────────────────────────────────────────────────────────
    BILLING         ("/billing",          "Facturation",     "💳", Section.GESTION),
    HOSPITALIZATION ("/hospitalization",  "Hospitalisation", "🏥", Section.GESTION),
    REPORTS         ("/reports",          "Rapports",        "📊", Section.GESTION),

    // ── ADMIN ─────────────────────────────────────────────────────────────────────
    ADMIN_USERS     ("/admin/users",      "Utilisateurs",    "⚙",  Section.ADMIN),
    ADMIN_DEPTS     ("/admin/departments","Départements",    "🏢", Section.ADMIN),
    ADMIN_INSURANCE ("/admin/insurance",  "Assureurs",       "🛡️", Section.ADMIN),
    ADMIN_ACTS      ("/admin/acts",       "Actes & tarifs",  "🧾", Section.ADMIN),
    ADMIN_LAB_TESTS ("/admin/lab-tests",  "Analyses",        "🔬", Section.ADMIN),
    ADMIN_ICD10     ("/admin/icd10",      "Diagnostics CIM-10", "🏷️", Section.ADMIN),
    ADMIN_AUDIT     ("/admin/audit",      "Journal d'audit", "📜", Section.ADMIN),
    ADMIN_CONFIG    ("/admin/config",     "Configuration",   "⚙️", Section.ADMIN);

    // ─────────────────────────────────────────────────────────────────────────────

    public enum Section { PRINCIPAL, SOINS, GESTION, ADMIN }

    public final String urlPrefix;
    public final String label;
    public final String icon;
    public final Section section;

    Module(String urlPrefix, String label, String icon, Section section) {
        this.urlPrefix = urlPrefix;
        this.label     = label;
        this.icon      = icon;
        this.section   = section;
    }
}
