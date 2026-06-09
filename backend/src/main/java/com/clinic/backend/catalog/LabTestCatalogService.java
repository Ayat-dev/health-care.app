package com.clinic.backend.catalog;

import com.clinic.backend.dto.LabTestCatalogDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class LabTestCatalogService {

    private final LabTestCatalogRepository labTestCatalogRepository;

    @Transactional(readOnly = true)
    public List<LabTestCatalog> listAll() {
        return labTestCatalogRepository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public List<LabTestCatalog> listActive() {
        return labTestCatalogRepository.findByActiveTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public LabTestCatalog getById(Long id) {
        return labTestCatalogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Analyse introuvable : " + id));
    }

    public LabTestCatalog create(LabTestCatalogDto dto) {
        String code = normalizeCode(dto.getCode());
        if (labTestCatalogRepository.existsByCodeIgnoreCase(code)) {
            throw new IllegalArgumentException("Ce code analyse existe déjà : " + code);
        }
        LabTestCatalog t = new LabTestCatalog();
        t.setCode(code);
        mapDtoToEntity(dto, t);
        return labTestCatalogRepository.save(t);
    }

    public LabTestCatalog update(Long id, LabTestCatalogDto dto) {
        LabTestCatalog t = getById(id);
        String code = normalizeCode(dto.getCode());
        labTestCatalogRepository.findByCodeIgnoreCase(code)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new IllegalArgumentException("Ce code analyse existe déjà : " + code);
                });
        t.setCode(code);
        mapDtoToEntity(dto, t);
        return labTestCatalogRepository.save(t);
    }

    public void toggleActive(Long id) {
        LabTestCatalog t = getById(id);
        t.setActive(!t.isActive());
        labTestCatalogRepository.save(t);
    }

    private void mapDtoToEntity(LabTestCatalogDto dto, LabTestCatalog t) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new IllegalArgumentException("Le nom de l'analyse est obligatoire");
        }
        if (dto.getPrice() == null || dto.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Le tarif doit être supérieur ou égal à 0");
        }
        t.setName(dto.getName().trim());
        t.setCategory(dto.getCategory());
        t.setPrice(dto.getPrice());
        t.setTurnaroundHours(dto.getTurnaroundHours());
        t.setReferenceRange(dto.getReferenceRange());
        t.setActive(dto.isActive());
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Le code analyse est obligatoire");
        }
        return code.trim().toUpperCase();
    }

    public LabTestCatalogDto toDto(LabTestCatalog t) {
        LabTestCatalogDto dto = new LabTestCatalogDto();
        dto.setId(t.getId());
        dto.setCode(t.getCode());
        dto.setName(t.getName());
        dto.setCategory(t.getCategory());
        dto.setPrice(t.getPrice());
        dto.setTurnaroundHours(t.getTurnaroundHours());
        dto.setReferenceRange(t.getReferenceRange());
        dto.setActive(t.isActive());
        dto.setCreatedAt(t.getCreatedAt());
        return dto;
    }
}
