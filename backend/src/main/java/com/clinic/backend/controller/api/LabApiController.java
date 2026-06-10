package com.clinic.backend.controller.api;

import com.clinic.backend.catalog.LabTestCatalog;
import com.clinic.backend.catalog.LabTestCatalogService;
import com.clinic.backend.dto.LabRequestDto;
import com.clinic.backend.dto.LabRequestItemDto;
import com.clinic.backend.dto.LabTestCatalogDto;
import com.clinic.backend.lab.LabService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/lab")
@RequiredArgsConstructor
public class LabApiController {

    private final LabService labService;
    private final LabTestCatalogService labTestCatalogService;

    // ── Catalogue ──────────────────────────────────────────────────────────────
    @GetMapping("/catalog")
    public List<LabTestCatalogDto> catalog() {
        return labTestCatalogService.listActive().stream()
                .map(labTestCatalogService::toDto).toList();
    }

    // ── Demandes ───────────────────────────────────────────────────────────────
    @GetMapping("/requests")
    public List<LabRequestDto> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority) {
        return labService.search(from, to, patientId, doctorId, status, priority)
                .stream().map(labService::toDto).toList();
    }

    @GetMapping("/requests/{id}")
    public LabRequestDto get(@PathVariable Long id) {
        return labService.getDtoById(id);
    }

    @PostMapping("/requests")
    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    public ResponseEntity<LabRequestDto> create(@RequestBody LabRequestDto dto) {
        Long id = labService.create(dto).getId();
        return ResponseEntity.ok(labService.getDtoById(id));
    }

    @PostMapping("/requests/{id}/results")
    @PreAuthorize("hasAnyRole('LABORANTIN','ADMIN')")
    public LabRequestDto enterResults(@PathVariable Long id, @RequestBody List<LabRequestItemDto> items) {
        labService.enterResults(id, items);
        return labService.getDtoById(id);
    }

    @PatchMapping("/requests/{id}/validate")
    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    public LabRequestDto validate(@PathVariable Long id) {
        labService.validate(id);
        return labService.getDtoById(id);
    }

    @PatchMapping("/requests/{id}/deliver")
    @PreAuthorize("hasAnyRole('LABORANTIN','MEDECIN','ADMIN')")
    public LabRequestDto deliver(@PathVariable Long id) {
        labService.deliver(id);
        return labService.getDtoById(id);
    }

    @PatchMapping("/requests/{id}/cancel")
    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    public LabRequestDto cancel(@PathVariable Long id) {
        labService.cancel(id);
        return labService.getDtoById(id);
    }
}
