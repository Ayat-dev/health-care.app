package com.clinic.backend.insurance;

import com.clinic.backend.dto.InsuranceProviderDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class InsuranceProviderService {

    private final InsuranceProviderRepository insuranceProviderRepository;

    @Transactional(readOnly = true)
    public List<InsuranceProvider> listAll() {
        return insuranceProviderRepository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public List<InsuranceProvider> listActive() {
        return insuranceProviderRepository.findByActiveTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public InsuranceProvider getById(Long id) {
        return insuranceProviderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Assureur introuvable : " + id));
    }

    public InsuranceProvider create(InsuranceProviderDto dto) {
        InsuranceProvider p = new InsuranceProvider();
        applyCode(dto, p, null);
        mapDtoToEntity(dto, p);
        return insuranceProviderRepository.save(p);
    }

    public InsuranceProvider update(Long id, InsuranceProviderDto dto) {
        InsuranceProvider p = getById(id);
        applyCode(dto, p, id);
        mapDtoToEntity(dto, p);
        return insuranceProviderRepository.save(p);
    }

    public void toggleActive(Long id) {
        InsuranceProvider p = getById(id);
        p.setActive(!p.isActive());
        insuranceProviderRepository.save(p);
    }

    // Code is optional for insurers, but must stay unique when provided.
    private void applyCode(InsuranceProviderDto dto, InsuranceProvider p, Long currentId) {
        String code = dto.getCode() == null || dto.getCode().isBlank()
                ? null
                : dto.getCode().trim().toUpperCase();
        if (code != null) {
            insuranceProviderRepository.findByCodeIgnoreCase(code)
                    .filter(other -> !other.getId().equals(currentId))
                    .ifPresent(other -> {
                        throw new IllegalArgumentException("Ce code assureur existe déjà : " + code);
                    });
        }
        p.setCode(code);
    }

    private void mapDtoToEntity(InsuranceProviderDto dto, InsuranceProvider p) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new IllegalArgumentException("Le nom de l'assureur est obligatoire");
        }
        p.setName(dto.getName().trim());
        p.setType(dto.getType());
        p.setCoveragePercent(dto.getCoveragePercent());
        p.setContact(dto.getContact());
        p.setActive(dto.isActive());
    }

    public InsuranceProviderDto toDto(InsuranceProvider p) {
        InsuranceProviderDto dto = new InsuranceProviderDto();
        dto.setId(p.getId());
        dto.setName(p.getName());
        dto.setCode(p.getCode());
        dto.setType(p.getType());
        dto.setCoveragePercent(p.getCoveragePercent());
        dto.setContact(p.getContact());
        dto.setActive(p.isActive());
        dto.setCreatedAt(p.getCreatedAt());
        return dto;
    }
}
