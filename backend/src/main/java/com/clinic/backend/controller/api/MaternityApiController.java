package com.clinic.backend.controller.api;

import com.clinic.backend.dto.MaternityRecordDto;
import com.clinic.backend.dto.PrenatalVisitDto;
import com.clinic.backend.maternity.MaternityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/maternity")
@RequiredArgsConstructor
public class MaternityApiController {

    private final MaternityService maternityService;

    @GetMapping
    public List<MaternityRecordDto> list(@RequestParam(required = false) String status) {
        return maternityService.search(status);
    }

    @GetMapping("/{id}")
    public MaternityRecordDto get(@PathVariable Long id) {
        return maternityService.getDtoById(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('MEDECIN','INFIRMIER','ADMIN')")
    public ResponseEntity<MaternityRecordDto> create(@RequestBody MaternityRecordDto dto) {
        Long id = maternityService.openRecord(dto).getId();
        return ResponseEntity.ok(maternityService.getDtoById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MEDECIN','INFIRMIER','ADMIN')")
    public MaternityRecordDto update(@PathVariable Long id, @RequestBody MaternityRecordDto dto) {
        maternityService.update(id, dto);
        return maternityService.getDtoById(id);
    }

    @PatchMapping("/{id}/deliver")
    @PreAuthorize("hasAnyRole('MEDECIN','INFIRMIER','ADMIN')")
    public MaternityRecordDto deliver(@PathVariable Long id, @RequestBody MaternityRecordDto dto) {
        maternityService.recordDelivery(id, dto);
        return maternityService.getDtoById(id);
    }

    @PatchMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('MEDECIN','INFIRMIER','ADMIN')")
    public MaternityRecordDto close(@PathVariable Long id) {
        maternityService.close(id);
        return maternityService.getDtoById(id);
    }

    // ── Consultations prénatales (CPN) ───────────────────────────────────────────
    @GetMapping("/{id}/visits")
    public List<PrenatalVisitDto> visits(@PathVariable Long id) {
        return maternityService.getDtoById(id).getVisits();
    }

    @PostMapping("/{id}/visits")
    @PreAuthorize("hasAnyRole('MEDECIN','INFIRMIER','ADMIN')")
    public MaternityRecordDto addVisit(@PathVariable Long id, @RequestBody PrenatalVisitDto dto) {
        maternityService.addVisit(id, dto);
        return maternityService.getDtoById(id);
    }

    @PutMapping("/{id}/visits/{visitId}")
    @PreAuthorize("hasAnyRole('MEDECIN','INFIRMIER','ADMIN')")
    public MaternityRecordDto updateVisit(@PathVariable Long id, @PathVariable Long visitId,
                                          @RequestBody PrenatalVisitDto dto) {
        maternityService.updateVisit(visitId, dto);
        return maternityService.getDtoById(id);
    }
}
