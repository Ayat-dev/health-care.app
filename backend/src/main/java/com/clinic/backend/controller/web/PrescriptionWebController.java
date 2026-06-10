package com.clinic.backend.controller.web;

import com.clinic.backend.clinicconfig.ClinicConfig;
import com.clinic.backend.clinicconfig.ClinicConfigService;
import com.clinic.backend.consultation.PrescriptionService;
import com.clinic.backend.dto.PrescriptionDto;
import com.clinic.backend.patient.Patient;
import com.clinic.backend.patient.PatientService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDate;
import java.time.Period;

@Controller
@RequestMapping("/prescriptions")
@RequiredArgsConstructor
public class PrescriptionWebController {

    private final PrescriptionService prescriptionService;
    private final PatientService patientService;
    private final ClinicConfigService clinicConfigService;

    /**
     * Standalone printable ordonnance (no sidebar layout). Print-to-PDF from the
     * browser; a binary PDF endpoint can be added later with a PDF library.
     */
    @GetMapping("/{id}/print")
    public String print(@PathVariable Long id, Model model) {
        PrescriptionDto prescription = prescriptionService.getDtoById(id);
        ClinicConfig config = clinicConfigService.getConfig();
        model.addAttribute("prescription", prescription);
        model.addAttribute("config", config);

        Integer age = null;
        if (prescription.getPatientId() != null) {
            Patient patient = patientService.getById(prescription.getPatientId());
            if (patient.getBirthDate() != null) {
                age = Period.between(patient.getBirthDate(), LocalDate.now()).getYears();
            }
        }
        model.addAttribute("patientAge", age);
        return "prescriptions/print";
    }
}
