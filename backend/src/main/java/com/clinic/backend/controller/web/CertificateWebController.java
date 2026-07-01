package com.clinic.backend.controller.web;

import com.clinic.backend.certificate.MedicalCertificate;
import com.clinic.backend.certificate.MedicalCertificateService;
import com.clinic.backend.clinicconfig.ClinicConfigService;
import com.clinic.backend.dto.MedicalCertificateDto;
import com.clinic.backend.export.PdfExportService;
import com.clinic.backend.patient.Patient;
import com.clinic.backend.patient.PatientService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.Period;
import java.util.HashMap;
import java.util.Map;

/**
 * Certificats médicaux (Tier E1). Émission réservée au médecin (acte médical, PHI —
 * ADMIN retiré comme pour les ordonnances, mur PHI P6). Le certificat imprimable/PDF
 * réutilise {@link PdfExportService} et le patron d'impression des ordonnances.
 */
@Controller
@RequestMapping("/certificates")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MEDECIN')")
public class CertificateWebController {

    private final MedicalCertificateService certificateService;
    private final PatientService patientService;
    private final ClinicConfigService clinicConfigService;
    private final PdfExportService pdfExportService;

    @GetMapping
    public String list(@RequestParam(required = false) Long patientId, Model model) {
        model.addAttribute("certificates",
                patientId != null ? certificateService.findForPatient(patientId) : certificateService.recent(50));
        model.addAttribute("patientId", patientId);
        return "certificates/list";
    }

    @GetMapping("/new")
    public String newForm(@RequestParam(required = false) Long consultationId,
                          @RequestParam(required = false) Long patientId,
                          Model model) {
        model.addAttribute("certificate", certificateService.prefill(consultationId, patientId));
        model.addAttribute("types", MedicalCertificateService.TYPES);
        return "certificates/form";
    }

    @PostMapping("/new")
    public String create(@ModelAttribute("certificate") MedicalCertificateDto dto,
                         RedirectAttributes ra, Model model) {
        try {
            MedicalCertificate saved = certificateService.create(dto);
            return "redirect:/certificates/" + saved.getId() + "/print";
        } catch (IllegalArgumentException | IllegalStateException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("types", MedicalCertificateService.TYPES);
            return "certificates/form";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("certificate", certificateService.getDtoById(id));
        model.addAttribute("types", MedicalCertificateService.TYPES);
        return "certificates/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @ModelAttribute("certificate") MedicalCertificateDto dto,
                         RedirectAttributes ra, Model model) {
        try {
            certificateService.update(id, dto);
            return "redirect:/certificates/" + id + "/print";
        } catch (IllegalArgumentException | IllegalStateException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("types", MedicalCertificateService.TYPES);
            return "certificates/form";
        }
    }

    /** Vue imprimable autonome (sert aussi de détail). Impression navigateur ou bouton PDF. */
    @GetMapping("/{id}/print")
    public String print(@PathVariable Long id, Model model) {
        model.addAllAttributes(certificateModel(id));
        model.addAttribute("pdf", false);
        return "certificates/print";
    }

    /** Certificat téléchargeable (PDF). */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        MedicalCertificateDto cert = certificateService.getDtoById(id);
        byte[] pdf = pdfExportService.renderTemplate("certificates/print", certificateModel(id));
        return BillingWebController.pdfInline(pdf, "certificat-" + cert.getCertificateNumber() + ".pdf");
    }

    private Map<String, Object> certificateModel(Long id) {
        MedicalCertificateDto cert = certificateService.getDtoById(id);
        Map<String, Object> model = new HashMap<>();
        model.put("certificate", cert);
        model.put("config", clinicConfigService.getConfig());
        Integer age = null;
        if (cert.getPatientId() != null) {
            Patient patient = patientService.getById(cert.getPatientId());
            if (patient.getBirthDate() != null) {
                age = Period.between(patient.getBirthDate(), LocalDate.now()).getYears();
            }
        }
        model.put("patientAge", age);
        return model;
    }
}
