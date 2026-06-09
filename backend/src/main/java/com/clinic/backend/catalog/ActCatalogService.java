package com.clinic.backend.catalog;

import com.clinic.backend.department.Department;
import com.clinic.backend.department.DepartmentRepository;
import com.clinic.backend.dto.ActCatalogDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ActCatalogService {

    private final ActCatalogRepository actCatalogRepository;
    private final DepartmentRepository departmentRepository;

    @Transactional(readOnly = true)
    public List<ActCatalog> listAll() {
        return actCatalogRepository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public List<ActCatalog> listActive() {
        return actCatalogRepository.findByActiveTrueOrderByNameAsc();
    }

    // Maps inside the transaction so the lazy department association is reachable.
    @Transactional(readOnly = true)
    public List<ActCatalogDto> listAllAsDto() {
        return actCatalogRepository.findAllByOrderByNameAsc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ActCatalogDto> listActiveAsDto() {
        return actCatalogRepository.findByActiveTrueOrderByNameAsc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ActCatalog getById(Long id) {
        return actCatalogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Acte introuvable : " + id));
    }

    @Transactional(readOnly = true)
    public ActCatalogDto getDtoById(Long id) {
        return toDto(getById(id));
    }

    public ActCatalog create(ActCatalogDto dto) {
        String code = normalizeCode(dto.getCode());
        if (actCatalogRepository.existsByCodeIgnoreCase(code)) {
            throw new IllegalArgumentException("Ce code acte existe déjà : " + code);
        }
        ActCatalog a = new ActCatalog();
        a.setCode(code);
        mapDtoToEntity(dto, a);
        return actCatalogRepository.save(a);
    }

    public ActCatalog update(Long id, ActCatalogDto dto) {
        ActCatalog a = getById(id);
        String code = normalizeCode(dto.getCode());
        actCatalogRepository.findByCodeIgnoreCase(code)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new IllegalArgumentException("Ce code acte existe déjà : " + code);
                });
        a.setCode(code);
        mapDtoToEntity(dto, a);
        return actCatalogRepository.save(a);
    }

    public void toggleActive(Long id) {
        ActCatalog a = getById(id);
        a.setActive(!a.isActive());
        actCatalogRepository.save(a);
    }

    private void mapDtoToEntity(ActCatalogDto dto, ActCatalog a) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new IllegalArgumentException("Le nom de l'acte est obligatoire");
        }
        if (dto.getPrice() == null || dto.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Le tarif doit être supérieur ou égal à 0");
        }
        a.setName(dto.getName().trim());
        a.setPrice(dto.getPrice());
        a.setActive(dto.isActive());
        a.setDepartment(resolveDepartment(dto.getDepartmentId()));
    }

    private Department resolveDepartment(Long departmentId) {
        if (departmentId == null) {
            return null;
        }
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> new IllegalArgumentException("Département introuvable : " + departmentId));
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Le code acte est obligatoire");
        }
        return code.trim().toUpperCase();
    }

    public ActCatalogDto toDto(ActCatalog a) {
        ActCatalogDto dto = new ActCatalogDto();
        dto.setId(a.getId());
        dto.setCode(a.getCode());
        dto.setName(a.getName());
        if (a.getDepartment() != null) {
            dto.setDepartmentId(a.getDepartment().getId());
            dto.setDepartmentName(a.getDepartment().getName());
        }
        dto.setPrice(a.getPrice());
        dto.setActive(a.isActive());
        dto.setCreatedAt(a.getCreatedAt());
        return dto;
    }
}
