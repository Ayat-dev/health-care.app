package com.clinic.backend.controller.api;

import com.clinic.backend.clinicconfig.ClinicConfigService;
import com.clinic.backend.consultation.PrescriptionService;
import com.clinic.backend.dto.PrescriptionDto;
import com.clinic.backend.export.PdfExportService;
import com.clinic.backend.patient.Patient;
import com.clinic.backend.patient.PatientService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.Period;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/prescriptions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MEDECIN','PHARMACIEN')") // PHI clinique — ADMIN retiré (P6)
public class PrescriptionApiController {

    private final PrescriptionService prescriptionService;
    private final PatientService patientService;
    private final ClinicConfigService clinicConfigService;
    private final PdfExportService pdfExportService;

    @GetMapping("/{id}")
    public PrescriptionDto get(@PathVariable Long id) {
        return prescriptionService.getDtoById(id);
    }

    @PutMapping("/{id}")
    public PrescriptionDto update(@PathVariable Long id, @RequestBody PrescriptionDto dto) {
        prescriptionService.update(id, dto);
        return prescriptionService.getDtoById(id);
    }

    /**
     * Ordonnance en PDF, accessible au client desktop (chaîne API JWT).
     * Les endpoints équivalents ({@code /prescriptions/{id}/pdf}) sont sur la chaîne
     * web/session, inaccessibles au desktop ; on réutilise ici le même
     * {@link PdfExportService} et la même vue d'impression {@code prescriptions/print}.
     */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        PrescriptionDto prescription = prescriptionService.getDtoById(id);
        byte[] pdf = pdfExportService.renderTemplate("prescriptions/print", prescriptionModel(prescription));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"ordonnance-" + prescription.getPrescriptionNumber() + ".pdf\"")
                .body(pdf);
    }

    /** Modèle attendu par la vue {@code prescriptions/print} (idem PrescriptionWebController). */
    private Map<String, Object> prescriptionModel(PrescriptionDto prescription) {
        Map<String, Object> model = new HashMap<>();
        model.put("prescription", prescription);
        model.put("config", clinicConfigService.getConfig());

        Integer age = null;
        if (prescription.getPatientId() != null) {
            Patient patient = patientService.getById(prescription.getPatientId());
            if (patient.getBirthDate() != null) {
                age = Period.between(patient.getBirthDate(), LocalDate.now()).getYears();
            }
        }
        model.put("patientAge", age);
        return model;
    }
}
