package com.clinic.backend.controller.api;

import com.clinic.backend.dto.PatientDto;
import com.clinic.backend.patient.Patient;
import com.clinic.backend.patient.PatientService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
public class PatientApiController {

    private final PatientService patientService;

    @GetMapping
    public Page<PatientDto> list(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return patientService.search(q, page, size).map(patientService::toDto);
    }

    @GetMapping("/{id}")
    public PatientDto get(@PathVariable Long id) {
        return patientService.toDto(patientService.getById(id));
    }

    @PostMapping
    public ResponseEntity<PatientDto> create(@RequestBody PatientDto dto) {
        Patient created = patientService.create(dto);
        return ResponseEntity.ok(patientService.toDto(created));
    }

    @PutMapping("/{id}")
    public PatientDto update(@PathVariable Long id, @RequestBody PatientDto dto) {
        return patientService.toDto(patientService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        patientService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
