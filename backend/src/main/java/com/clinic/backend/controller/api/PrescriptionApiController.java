package com.clinic.backend.controller.api;

import com.clinic.backend.consultation.PrescriptionService;
import com.clinic.backend.dto.PrescriptionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/prescriptions")
@RequiredArgsConstructor
public class PrescriptionApiController {

    private final PrescriptionService prescriptionService;

    @GetMapping("/{id}")
    public PrescriptionDto get(@PathVariable Long id) {
        return prescriptionService.getDtoById(id);
    }

    @PutMapping("/{id}")
    public PrescriptionDto update(@PathVariable Long id, @RequestBody PrescriptionDto dto) {
        prescriptionService.update(id, dto);
        return prescriptionService.getDtoById(id);
    }
}
