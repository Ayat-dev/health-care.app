package com.clinic.backend.controller.api;

import com.clinic.backend.catalog.Icd10Code;
import com.clinic.backend.catalog.Icd10Service;
import com.clinic.backend.dto.Icd10CodeDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Catalogue CIM-10 (P2.2). {@code GET /search} sert l'auto-complétion (tous les
 * utilisateurs authentifiés) ; les écritures sont réservées à l'ADMIN.
 */
@RestController
@RequestMapping("/api/icd10")
@RequiredArgsConstructor
public class Icd10ApiController {

    private final Icd10Service icd10Service;

    @GetMapping("/search")
    public List<Icd10CodeDto> search(@RequestParam("q") String q) {
        return icd10Service.search(q);
    }

    @GetMapping
    public List<Icd10CodeDto> list() {
        return icd10Service.listAll().stream().map(icd10Service::toDto).toList();
    }

    @GetMapping("/{id}")
    public Icd10CodeDto get(@PathVariable Long id) {
        return icd10Service.toDto(icd10Service.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Icd10CodeDto> create(@RequestBody Icd10CodeDto dto) {
        Icd10Code created = icd10Service.create(dto);
        return ResponseEntity.ok(icd10Service.toDto(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Icd10CodeDto update(@PathVariable Long id, @RequestBody Icd10CodeDto dto) {
        return icd10Service.toDto(icd10Service.update(id, dto));
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public Icd10CodeDto toggle(@PathVariable Long id) {
        icd10Service.toggleActive(id);
        return icd10Service.toDto(icd10Service.getById(id));
    }
}
