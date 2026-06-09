package com.clinic.backend.controller.api;

import com.clinic.backend.catalog.LabTestCatalog;
import com.clinic.backend.catalog.LabTestCatalogService;
import com.clinic.backend.dto.LabTestCatalogDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/lab-tests")
@RequiredArgsConstructor
public class LabTestCatalogApiController {

    private final LabTestCatalogService labTestCatalogService;

    @GetMapping
    public List<LabTestCatalogDto> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        List<LabTestCatalog> tests = activeOnly
                ? labTestCatalogService.listActive()
                : labTestCatalogService.listAll();
        return tests.stream().map(labTestCatalogService::toDto).toList();
    }

    @GetMapping("/{id}")
    public LabTestCatalogDto get(@PathVariable Long id) {
        return labTestCatalogService.toDto(labTestCatalogService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LabTestCatalogDto> create(@RequestBody LabTestCatalogDto dto) {
        LabTestCatalog created = labTestCatalogService.create(dto);
        return ResponseEntity.ok(labTestCatalogService.toDto(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public LabTestCatalogDto update(@PathVariable Long id, @RequestBody LabTestCatalogDto dto) {
        return labTestCatalogService.toDto(labTestCatalogService.update(id, dto));
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public LabTestCatalogDto toggle(@PathVariable Long id) {
        labTestCatalogService.toggleActive(id);
        return labTestCatalogService.toDto(labTestCatalogService.getById(id));
    }
}
