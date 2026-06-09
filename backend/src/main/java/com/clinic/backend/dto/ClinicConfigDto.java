package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Carries clinic configuration between the admin UI and {@code ClinicConfigService}.
 * Booleans default to {@code false} here on purpose: unchecked HTML checkboxes are
 * not submitted, so an absent flag must read as "off".
 */
@Getter @Setter
public class ClinicConfigDto {
    private Long id;
    // Identité
    private String name;
    private String slogan;
    private String address;
    private String phone;
    private String email;
    private String website;
    private String logoUrl;
    private String currency = "XOF";
    private String timezone = "Africa/Dakar";
    private String defaultLanguage = "fr";
    // Modules
    private boolean modulePharmacy;
    private boolean moduleLab;
    private boolean moduleMaternity;
    private boolean moduleDental;
    private boolean moduleRadiology;
    private boolean moduleHospitalization;
    // Paiements
    private boolean mobileMoneyEnabled;
    private String mobileMoneyProvider;
    private boolean insuranceEnabled;
    // Numérotation
    private String patientRecordPrefix = "PAT";
    private String invoicePrefix = "FAC";
    private String prescriptionPrefix = "ORD";
}
