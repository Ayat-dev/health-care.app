package com.clinic.backend.controller.api;

import com.clinic.backend.dto.RadiologyExamCatalogDto;
import com.clinic.backend.dto.RadiologyRequestDto;
import com.clinic.backend.radiology.RadiologyExamCatalog;
import com.clinic.backend.radiology.RadiologyService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/radiology")
@RequiredArgsConstructor
public class RadiologyApiController {

    private final RadiologyService radiologyService;

    // ── Catalogue ──────────────────────────────────────────────────────────────
    @GetMapping("/catalog")
    public List<RadiologyExamCatalogDto> catalog() {
        return radiologyService.listCatalog().stream().map(this::toCatalogDto).toList();
    }

    // ── Demandes ───────────────────────────────────────────────────────────────
    @GetMapping("/requests")
    public List<RadiologyRequestDto> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority) {
        return radiologyService.searchDto(from, to, patientId, doctorId, status, priority);
    }

    @GetMapping("/requests/{id}")
    public RadiologyRequestDto get(@PathVariable Long id) {
        return radiologyService.getDtoById(id);
    }

    @PostMapping("/requests")
    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    public ResponseEntity<RadiologyRequestDto> create(@RequestBody RadiologyRequestDto dto) {
        Long id = radiologyService.create(dto).getId();
        return ResponseEntity.ok(radiologyService.getDtoById(id));
    }

    @PostMapping("/requests/{id}/report")
    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    public RadiologyRequestDto saveReport(@PathVariable Long id, @RequestBody RadiologyRequestDto dto) {
        radiologyService.saveReport(id, dto.getFindings(), dto.getConclusion());
        return radiologyService.getDtoById(id);
    }

    @PostMapping("/requests/{id}/images")
    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    public RadiologyRequestDto uploadImage(@PathVariable Long id,
                                           @RequestParam("file") MultipartFile file,
                                           @RequestParam(required = false) String caption) {
        radiologyService.addImage(id, file, caption);
        return radiologyService.getDtoById(id);
    }

    @PatchMapping("/requests/{id}/validate")
    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    public RadiologyRequestDto validate(@PathVariable Long id) {
        radiologyService.validate(id);
        return radiologyService.getDtoById(id);
    }

    @PatchMapping("/requests/{id}/deliver")
    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    public RadiologyRequestDto deliver(@PathVariable Long id) {
        radiologyService.deliver(id);
        return radiologyService.getDtoById(id);
    }

    @PatchMapping("/requests/{id}/cancel")
    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    public RadiologyRequestDto cancel(@PathVariable Long id) {
        radiologyService.cancel(id);
        return radiologyService.getDtoById(id);
    }

    private RadiologyExamCatalogDto toCatalogDto(RadiologyExamCatalog e) {
        RadiologyExamCatalogDto dto = new RadiologyExamCatalogDto();
        dto.setId(e.getId());
        dto.setCode(e.getCode());
        dto.setName(e.getName());
        dto.setType(e.getType());
        dto.setRegion(e.getRegion());
        dto.setPrice(e.getPrice());
        dto.setActive(e.isActive());
        return dto;
    }
}
