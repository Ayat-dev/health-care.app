package com.clinic.backend.controller.api;

import com.clinic.backend.consultation.ConsultationService;
import com.clinic.backend.consultation.PrescriptionService;
import com.clinic.backend.dto.ConsultationDto;
import com.clinic.backend.dto.PrescriptionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/consultations")
@RequiredArgsConstructor
public class ConsultationApiController {

    private final ConsultationService consultationService;
    private final PrescriptionService prescriptionService;

    @GetMapping
    public List<ConsultationDto> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) String status) {
        return consultationService.search(from, to, doctorId, patientId, status)
                .stream().map(consultationService::toDto).toList();
    }

    @GetMapping("/{id}")
    public ConsultationDto get(@PathVariable Long id) {
        return consultationService.getDtoById(id);
    }

    @PostMapping
    public ResponseEntity<ConsultationDto> create(@RequestBody ConsultationDto dto) {
        Long id = consultationService.create(dto).getId();
        return ResponseEntity.ok(consultationService.getDtoById(id));
    }

    @PutMapping("/{id}")
    public ConsultationDto update(@PathVariable Long id, @RequestBody ConsultationDto dto) {
        consultationService.update(id, dto);
        return consultationService.getDtoById(id);
    }

    @PatchMapping("/{id}/complete")
    public ConsultationDto complete(@PathVariable Long id) {
        consultationService.complete(id);
        return consultationService.getDtoById(id);
    }

    @PatchMapping("/{id}/cancel")
    public ConsultationDto cancel(@PathVariable Long id) {
        consultationService.cancel(id);
        return consultationService.getDtoById(id);
    }

    // ── Ordonnance liée ───────────────────────────────────────────────────
    @GetMapping("/{id}/prescription")
    public ResponseEntity<PrescriptionDto> getPrescription(@PathVariable Long id) {
        PrescriptionDto dto = prescriptionService.findDtoForConsultation(id);
        return dto != null ? ResponseEntity.ok(dto) : ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/prescription")
    public ResponseEntity<PrescriptionDto> createPrescription(@PathVariable Long id,
                                                              @RequestBody PrescriptionDto dto) {
        Long pid = prescriptionService.createForConsultation(id, dto).getId();
        return ResponseEntity.ok(prescriptionService.getDtoById(pid));
    }
}
