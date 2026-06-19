package com.clinic.backend.catalog;

import com.clinic.backend.config.ResourceNotFoundException;
import com.clinic.backend.dto.Icd10CodeDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class Icd10Service {

    /** Plafond de résultats renvoyés à l'auto-complétion. */
    public static final int SEARCH_LIMIT = 20;

    private final Icd10CodeRepository icd10CodeRepository;

    @Transactional(readOnly = true)
    public List<Icd10Code> listAll() {
        return icd10CodeRepository.findAllByOrderByCodeAsc();
    }

    /** Auto-complétion : renvoie au plus {@link #SEARCH_LIMIT} codes actifs correspondant à {@code q}. */
    @Transactional(readOnly = true)
    public List<Icd10CodeDto> search(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        return icd10CodeRepository.search(q.trim(), PageRequest.of(0, SEARCH_LIMIT))
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Icd10Code getById(Long id) {
        return icd10CodeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Code CIM-10 introuvable : " + id));
    }

    public Icd10Code create(Icd10CodeDto dto) {
        String code = normalizeCode(dto.getCode());
        if (icd10CodeRepository.existsByCodeIgnoreCase(code)) {
            throw new IllegalArgumentException("Ce code CIM-10 existe déjà : " + code);
        }
        Icd10Code c = new Icd10Code();
        c.setCode(code);
        mapDtoToEntity(dto, c);
        return icd10CodeRepository.save(c);
    }

    public Icd10Code update(Long id, Icd10CodeDto dto) {
        Icd10Code c = getById(id);
        String code = normalizeCode(dto.getCode());
        icd10CodeRepository.findByCodeIgnoreCase(code)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new IllegalArgumentException("Ce code CIM-10 existe déjà : " + code);
                });
        c.setCode(code);
        mapDtoToEntity(dto, c);
        return icd10CodeRepository.save(c);
    }

    public void toggleActive(Long id) {
        Icd10Code c = getById(id);
        c.setActive(!c.isActive());
        icd10CodeRepository.save(c);
    }

    private void mapDtoToEntity(Icd10CodeDto dto, Icd10Code c) {
        if (dto.getTitle() == null || dto.getTitle().isBlank()) {
            throw new IllegalArgumentException("Le libellé du diagnostic est obligatoire");
        }
        c.setTitle(dto.getTitle().trim());
        c.setCategory(dto.getCategory() != null && !dto.getCategory().isBlank()
                ? dto.getCategory().trim() : null);
        c.setActive(dto.isActive());
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Le code CIM-10 est obligatoire");
        }
        return code.trim().toUpperCase();
    }

    public Icd10CodeDto toDto(Icd10Code c) {
        Icd10CodeDto dto = new Icd10CodeDto();
        dto.setId(c.getId());
        dto.setCode(c.getCode());
        dto.setTitle(c.getTitle());
        dto.setCategory(c.getCategory());
        dto.setActive(c.isActive());
        dto.setCreatedAt(c.getCreatedAt());
        return dto;
    }
}
