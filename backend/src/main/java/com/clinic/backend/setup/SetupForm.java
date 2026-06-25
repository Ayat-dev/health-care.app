package com.clinic.backend.setup;

import lombok.Getter;
import lombok.Setter;

/**
 * Données saisies par l'administrateur lors de l'assistant de première installation
 * ({@code /setup}). Regroupe en un seul formulaire : le compte administrateur initial,
 * l'identité de la clinique et les modules à activer. Les valeurs par défaut reflètent
 * le marché cible (Niger : XOF, français).
 *
 * @see SetupService#complete(SetupForm)
 */
@Getter @Setter
public class SetupForm {

    // ── Compte administrateur ────────────────────────────────────────────────
    private String adminUsername;
    private String adminFullName;
    private String adminPassword;
    private String adminPasswordConfirm;

    // ── Identité de la clinique ──────────────────────────────────────────────
    private String clinicName;
    private String clinicAddress;
    private String clinicPhone;
    private String clinicEmail;
    private String currency = "XOF";
    private String defaultLanguage = "fr";

    // ── Modules activés ──────────────────────────────────────────────────────
    // Pré-cochés par défaut comme dans clinic_config (V4).
    private boolean modulePharmacy = true;
    private boolean moduleLab = true;
    private boolean moduleMaternity = false;
    private boolean moduleRadiology = false;
    private boolean moduleHospitalization = false;
    private boolean moduleDental = false;
}
