package com.clinic.backend.department;

import com.clinic.backend.dto.DepartmentDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    // ── Listes ────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<Department> listAll() {
        return departmentRepository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public List<Department> listActive() {
        return departmentRepository.findByActiveTrueOrderByNameAsc();
    }

    // ── Détail ────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public Department getById(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Département introuvable : " + id));
    }

    // ── Création ──────────────────────────────────────────────────────────
    public Department create(DepartmentDto dto) {
        String code = normalizeCode(dto.getCode());
        if (departmentRepository.existsByCodeIgnoreCase(code)) {
            throw new IllegalArgumentException("Ce code département existe déjà : " + code);
        }
        Department d = new Department();
        d.setCode(code);
        mapDtoToEntity(dto, d);
        return departmentRepository.save(d);
    }

    // ── Modification ──────────────────────────────────────────────────────
    public Department update(Long id, DepartmentDto dto) {
        Department d = getById(id);
        String code = normalizeCode(dto.getCode());
        departmentRepository.findByCodeIgnoreCase(code)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new IllegalArgumentException("Ce code département existe déjà : " + code);
                });
        d.setCode(code);
        mapDtoToEntity(dto, d);
        return departmentRepository.save(d);
    }

    // ── Activation / désactivation (suppression logique via is_active) ──────
    public void toggleActive(Long id) {
        Department d = getById(id);
        d.setActive(!d.isActive());
        departmentRepository.save(d);
    }

    // ── Mapping DTO → entité ──────────────────────────────────────────────
    private void mapDtoToEntity(DepartmentDto dto, Department d) {
        d.setName(dto.getName());
        d.setDescription(dto.getDescription());
        d.setColor(dto.getColor());
        d.setActive(dto.isActive());
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Le code département est obligatoire");
        }
        return code.trim().toUpperCase();
    }

    // ── DTO depuis entité ─────────────────────────────────────────────────
    public DepartmentDto toDto(Department d) {
        DepartmentDto dto = new DepartmentDto();
        dto.setId(d.getId());
        dto.setCode(d.getCode());
        dto.setName(d.getName());
        dto.setDescription(d.getDescription());
        dto.setColor(d.getColor());
        dto.setActive(d.isActive());
        dto.setCreatedAt(d.getCreatedAt());
        return dto;
    }
}
