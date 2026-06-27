package com.clinic.backend.clinicconfig;

import com.clinic.backend.dto.ClinicConfigDto;
import com.clinic.backend.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and updates the {@link ClinicConfig} row of the <b>current clinic</b> (multi-tenant P4.2).
 * If a clinic has no config yet, a default one is created on demand so callers never deal with an
 * absent config. Hors contexte clinique (SUPER_ADMIN / tâche sans tenant), renvoie un défaut
 * transitoire non persisté (jamais de clinic_id sentinelle en base).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ClinicConfigService {

    private final ClinicConfigRepository clinicConfigRepository;

    @Transactional(readOnly = true)
    public ClinicConfig getConfig() {
        Long clinicId = TenantContext.currentClinicId();
        if (clinicId == null) {
            // Aucune clinique courante → défaut transitoire (non sauvegardé).
            ClinicConfig c = new ClinicConfig();
            c.setName("ClinicApp");
            return c;
        }
        return clinicConfigRepository.findByClinicId(clinicId)
                .orElseGet(() -> {
                    ClinicConfig c = new ClinicConfig();
                    c.setName("ClinicApp");
                    c.setClinicId(clinicId);
                    return clinicConfigRepository.save(c);
                });
    }

    public ClinicConfig update(ClinicConfigDto dto) {
        ClinicConfig c = getConfig();
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new IllegalArgumentException("Le nom de la clinique est obligatoire");
        }
        // Identité
        c.setName(dto.getName().trim());
        c.setSlogan(dto.getSlogan());
        c.setAddress(dto.getAddress());
        c.setPhone(dto.getPhone());
        c.setEmail(dto.getEmail());
        c.setWebsite(dto.getWebsite());
        c.setLogoUrl(dto.getLogoUrl());
        c.setCurrency(dto.getCurrency());
        c.setTimezone(dto.getTimezone());
        c.setDefaultLanguage(dto.getDefaultLanguage());
        // Modules
        c.setModulePharmacy(dto.isModulePharmacy());
        c.setModuleLab(dto.isModuleLab());
        c.setModuleMaternity(dto.isModuleMaternity());
        c.setModuleDental(dto.isModuleDental());
        c.setModuleRadiology(dto.isModuleRadiology());
        c.setModuleHospitalization(dto.isModuleHospitalization());
        // Paiements
        c.setMobileMoneyEnabled(dto.isMobileMoneyEnabled());
        c.setMobileMoneyProvider(dto.getMobileMoneyProvider());
        c.setInsuranceEnabled(dto.isInsuranceEnabled());
        // QR marchand paiement mobile
        c.setAmanataQrUrl(dto.getAmanataQrUrl());
        c.setAmanataMerchantId(dto.getAmanataMerchantId());
        c.setMynitaQrUrl(dto.getMynitaQrUrl());
        c.setMynitaMerchantId(dto.getMynitaMerchantId());
        // Numérotation
        c.setPatientRecordPrefix(dto.getPatientRecordPrefix());
        c.setInvoicePrefix(dto.getInvoicePrefix());
        c.setPrescriptionPrefix(dto.getPrescriptionPrefix());
        return clinicConfigRepository.save(c);
    }

    @Transactional(readOnly = true)
    public ClinicConfigDto toDto(ClinicConfig c) {
        ClinicConfigDto dto = new ClinicConfigDto();
        dto.setId(c.getId());
        dto.setName(c.getName());
        dto.setSlogan(c.getSlogan());
        dto.setAddress(c.getAddress());
        dto.setPhone(c.getPhone());
        dto.setEmail(c.getEmail());
        dto.setWebsite(c.getWebsite());
        dto.setLogoUrl(c.getLogoUrl());
        dto.setCurrency(c.getCurrency());
        dto.setTimezone(c.getTimezone());
        dto.setDefaultLanguage(c.getDefaultLanguage());
        dto.setModulePharmacy(c.isModulePharmacy());
        dto.setModuleLab(c.isModuleLab());
        dto.setModuleMaternity(c.isModuleMaternity());
        dto.setModuleDental(c.isModuleDental());
        dto.setModuleRadiology(c.isModuleRadiology());
        dto.setModuleHospitalization(c.isModuleHospitalization());
        dto.setMobileMoneyEnabled(c.isMobileMoneyEnabled());
        dto.setMobileMoneyProvider(c.getMobileMoneyProvider());
        dto.setInsuranceEnabled(c.isInsuranceEnabled());
        dto.setAmanataQrUrl(c.getAmanataQrUrl());
        dto.setAmanataMerchantId(c.getAmanataMerchantId());
        dto.setMynitaQrUrl(c.getMynitaQrUrl());
        dto.setMynitaMerchantId(c.getMynitaMerchantId());
        dto.setPatientRecordPrefix(c.getPatientRecordPrefix());
        dto.setInvoicePrefix(c.getInvoicePrefix());
        dto.setPrescriptionPrefix(c.getPrescriptionPrefix());
        return dto;
    }
}
