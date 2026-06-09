package com.clinic.backend.clinicconfig;

import com.clinic.backend.dto.ClinicConfigDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and updates the single {@link ClinicConfig} row. If the seed row is ever
 * missing (e.g. a wiped dev DB before V4 ran), a default one is created on demand
 * so callers never have to deal with an absent config.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ClinicConfigService {

    private final ClinicConfigRepository clinicConfigRepository;

    @Transactional(readOnly = true)
    public ClinicConfig getConfig() {
        return clinicConfigRepository.findFirstByOrderByIdAsc()
                .orElseGet(() -> {
                    ClinicConfig c = new ClinicConfig();
                    c.setName("ClinicApp");
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
        dto.setPatientRecordPrefix(c.getPatientRecordPrefix());
        dto.setInvoicePrefix(c.getInvoicePrefix());
        dto.setPrescriptionPrefix(c.getPrescriptionPrefix());
        return dto;
    }
}
