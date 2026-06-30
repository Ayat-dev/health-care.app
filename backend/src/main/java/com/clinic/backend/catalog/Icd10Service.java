package com.clinic.backend.catalog;

import com.clinic.backend.config.ResourceNotFoundException;
import com.clinic.backend.dto.Icd10CodeDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

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

    /**
     * Découpe une chaîne de codes « J06.9, R50.9 » en codes normalisés (uppercase, sans
     * blanc), <b>distincts</b> et dans l'ordre de saisie. Utilisé pour résoudre les
     * libellés (fiche consultation) et agréger les « top pathologies » (épidémiologie, D4c).
     */
    public static List<String> splitCodes(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String c = part.trim().toUpperCase();
            if (!c.isEmpty()) {
                out.add(c);
            }
        }
        return new ArrayList<>(out);
    }

    /** Libellé d'affichage « CODE — Titre » (ou le code seul si inconnu au catalogue). */
    public static String displayLabel(String code, String title) {
        return (title == null || title.isBlank()) ? code : code + " — " + title;
    }

    /** Résout un lot de codes (normalisés uppercase) en map code→titre ; codes inconnus absents. */
    @Transactional(readOnly = true)
    public Map<String, String> titlesByCode(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Map.of();
        }
        Map<String, String> map = new HashMap<>();
        for (Icd10Code c : icd10CodeRepository.findByCodeInUpper(codes)) {
            map.put(c.getCode().toUpperCase(), c.getTitle());
        }
        return map;
    }

    /**
     * Résout une chaîne de codes CIM-10 (séparés par virgule) en libellés ordonnés pour la
     * fiche consultation (D4c). Chaque entrée porte le code et, si présent au catalogue, son
     * titre (sinon {@code title} null → affiché code seul).
     */
    @Transactional(readOnly = true)
    public List<Icd10CodeDto> resolveCodes(String raw) {
        List<String> codes = splitCodes(raw);
        if (codes.isEmpty()) {
            return List.of();
        }
        Map<String, String> titles = titlesByCode(codes);
        List<Icd10CodeDto> out = new ArrayList<>();
        for (String code : codes) {
            Icd10CodeDto dto = new Icd10CodeDto();
            dto.setCode(code);
            dto.setTitle(titles.get(code));
            out.add(dto);
        }
        return out;
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
