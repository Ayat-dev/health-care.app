package com.clinic.backend.controller.api;

import com.clinic.backend.catalog.ActCatalog;
import com.clinic.backend.catalog.ActCatalogService;
import com.clinic.backend.dto.ActCatalogDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/acts")
@RequiredArgsConstructor
public class ActCatalogApiController {

    private final ActCatalogService actCatalogService;

    @GetMapping
    public List<ActCatalogDto> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return activeOnly
                ? actCatalogService.listActiveAsDto()
                : actCatalogService.listAllAsDto();
    }

    @GetMapping("/{id}")
    public ActCatalogDto get(@PathVariable Long id) {
        return actCatalogService.getDtoById(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ActCatalogDto> create(@RequestBody ActCatalogDto dto) {
        ActCatalog created = actCatalogService.create(dto);
        return ResponseEntity.ok(actCatalogService.toDto(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ActCatalogDto update(@PathVariable Long id, @RequestBody ActCatalogDto dto) {
        return actCatalogService.toDto(actCatalogService.update(id, dto));
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ActCatalogDto toggle(@PathVariable Long id) {
        actCatalogService.toggleActive(id);
        return actCatalogService.getDtoById(id);
    }
}
