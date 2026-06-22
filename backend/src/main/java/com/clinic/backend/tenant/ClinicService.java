package com.clinic.backend.tenant;

import com.clinic.backend.config.ResourceNotFoundException;
import com.clinic.backend.dto.ClinicDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gestion du registre des cliniques (P4.2) — réservée au SUPER_ADMIN.
 * Le code est unique et immuable après création (sert d'ancrage stable).
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ClinicService {

    private final ClinicRepository clinicRepository;

    @Transactional(readOnly = true)
    public List<Clinic> listAll() {
        return clinicRepository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Clinic getById(Long id) {
        return clinicRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Clinique introuvable : " + id));
    }

    public Clinic create(ClinicDto dto) {
        String code = normalizeCode(dto.getCode());
        if (code.isEmpty())
            throw new IllegalArgumentException("Le code de la clinique est obligatoire.");
        if (clinicRepository.existsByCodeIgnoreCase(code))
            throw new IllegalArgumentException("Ce code de clinique existe déjà : " + code);
        if (dto.getName() == null || dto.getName().isBlank())
            throw new IllegalArgumentException("Le nom de la clinique est obligatoire.");

        Clinic c = new Clinic();
        c.setCode(code);
        apply(c, dto);
        Clinic saved = clinicRepository.save(c);
        log.info("Clinique créée : {} ({})", saved.getName(), saved.getCode());
        return saved;
    }

    public Clinic update(Long id, ClinicDto dto) {
        Clinic c = getById(id);
        if (dto.getName() == null || dto.getName().isBlank())
            throw new IllegalArgumentException("Le nom de la clinique est obligatoire.");
        apply(c, dto); // le code n'est pas modifiable
        return clinicRepository.save(c);
    }

    public void toggleActive(Long id) {
        Clinic c = getById(id);
        c.setActive(!c.isActive());
        log.info("Clinique {} : active = {}", c.getCode(), c.isActive());
    }

    public ClinicDto toDto(Clinic c) {
        ClinicDto dto = new ClinicDto();
        dto.setId(c.getId());
        dto.setCode(c.getCode());
        dto.setName(c.getName());
        dto.setAddress(c.getAddress());
        dto.setPhone(c.getPhone());
        dto.setEmail(c.getEmail());
        dto.setActive(c.isActive());
        dto.setCreatedAt(c.getCreatedAt());
        return dto;
    }

    private void apply(Clinic c, ClinicDto dto) {
        c.setName(dto.getName().trim());
        c.setAddress(dto.getAddress());
        c.setPhone(dto.getPhone());
        c.setEmail(dto.getEmail());
        c.setActive(dto.isActive());
    }

    private String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }
}
