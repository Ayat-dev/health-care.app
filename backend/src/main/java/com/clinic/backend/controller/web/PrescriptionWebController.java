package com.clinic.backend.controller.web;

import com.clinic.backend.clinicconfig.ClinicConfigService;
import com.clinic.backend.consultation.PrescriptionService;
import com.clinic.backend.controller.web.BillingWebController;
import com.clinic.backend.dto.PrescriptionDto;
import com.clinic.backend.export.PdfExportService;
import com.clinic.backend.patient.Patient;
import com.clinic.backend.patient.PatientService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDate;
import java.time.Period;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/prescriptions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MEDECIN','PHARMACIEN')") // PHI clinique — ADMIN retiré (P6)
public class PrescriptionWebController {

    private final PrescriptionService prescriptionService;
    private final PatientService patientService;
    private final ClinicConfigService clinicConfigService;
    private final PdfExportService pdfExportService;

    /** Ordonnance imprimable autonome (sans sidebar). Impression navigateur ou bouton PDF. */
    @GetMapping("/{id}/print")
    public String print(@PathVariable Long id, Model model) {
        model.addAllAttributes(prescriptionModel(id));
        model.addAttribute("pdf", false); // affiche la toolbar dans le navigateur
        return "prescriptions/print";
    }

    /** Ordonnance téléchargeable (PDF). */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        PrescriptionDto prescription = prescriptionService.getDtoById(id);
        byte[] pdf = pdfExportService.renderTemplate("prescriptions/print", prescriptionModel(id));
        return BillingWebController.pdfInline(pdf, "ordonnance-" + prescription.getPrescriptionNumber() + ".pdf");
    }

    private Map<String, Object> prescriptionModel(Long id) {
        PrescriptionDto prescription = prescriptionService.getDtoById(id);
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
