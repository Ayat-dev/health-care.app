package com.clinic.backend.clinicconfig;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One row per clinic (multi-tenant P4.2) holding that clinic's identity, payment options and
 * feature flags. {@code ClinicConfigService} reads/updates the row of the <b>current</b> clinic.
 * <p>
 * Cloisonnement <b>applicatif</b> (colonne {@code clinic_id} simple, résolue via
 * {@code TenantContext}) plutôt que {@code @TenantId} Hibernate : le singleton est créé à la volée
 * et à l'installation (hors contexte de requête / de session avec tenant), ce qui se prête mal au
 * timing de résolution du tenant à l'ouverture de session. Même approche que {@code users.clinic_id}.
 */
@Entity
@Table(name = "clinic_config")
@Getter @Setter @NoArgsConstructor
public class ClinicConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Clinique propriétaire (P4.2) — une config par clinique. Cloisonnement applicatif. */
    @Column(name = "clinic_id", nullable = false)
    private Long clinicId;

    // ── Identité ────────────────────────────────────────────────────────────
    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 255)
    private String slogan;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(length = 30)
    private String phone;

    @Column(length = 120)
    private String email;

    @Column(length = 255)
    private String website;

    @Column(name = "logo_url", length = 255)
    private String logoUrl;

    @Column(nullable = false, length = 10)
    private String currency = "XOF";

    @Column(nullable = false, length = 50)
    private String timezone = "Africa/Niamey";

    @Column(name = "default_language", nullable = false, length = 10)
    private String defaultLanguage = "fr";

    // ── Modules activés (feature flags) ─────────────────────────────────────
    @Column(name = "module_pharmacy", nullable = false)
    private boolean modulePharmacy = true;

    @Column(name = "module_lab", nullable = false)
    private boolean moduleLab = true;

    @Column(name = "module_maternity", nullable = false)
    private boolean moduleMaternity = false;

    @Column(name = "module_dental", nullable = false)
    private boolean moduleDental = false;

    @Column(name = "module_radiology", nullable = false)
    private boolean moduleRadiology = false;

    @Column(name = "module_hospitalization", nullable = false)
    private boolean moduleHospitalization = false;

    // ── Paiements ───────────────────────────────────────────────────────────
    @Column(name = "mobile_money_enabled", nullable = false)
    private boolean mobileMoneyEnabled = false;

    @Column(name = "mobile_money_provider", length = 30)
    private String mobileMoneyProvider; // ORANGE_MONEY, MTN_MOMO, WAVE

    @Column(name = "insurance_enabled", nullable = false)
    private boolean insuranceEnabled = false;

    // QR marchand pour le paiement mobile (confirmation manuelle, pas d'API).
    @Column(name = "amanata_qr_url", length = 255)
    private String amanataQrUrl;

    @Column(name = "amanata_merchant_id", length = 60)
    private String amanataMerchantId;

    @Column(name = "mynita_qr_url", length = 255)
    private String mynitaQrUrl;

    @Column(name = "mynita_merchant_id", length = 60)
    private String mynitaMerchantId;

    // ── Numérotation ────────────────────────────────────────────────────────
    @Column(name = "patient_record_prefix", nullable = false, length = 10)
    private String patientRecordPrefix = "PAT";

    @Column(name = "invoice_prefix", nullable = false, length = 10)
    private String invoicePrefix = "FAC";

    @Column(name = "prescription_prefix", nullable = false, length = 10)
    private String prescriptionPrefix = "ORD";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
