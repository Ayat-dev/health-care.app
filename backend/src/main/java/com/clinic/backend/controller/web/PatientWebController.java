package com.clinic.backend.controller.web;

import com.clinic.backend.dto.PatientDto;
import com.clinic.backend.patient.Patient;
import com.clinic.backend.patient.PatientOverviewService;
import com.clinic.backend.patient.PatientService;
import com.clinic.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Interface web Thymeleaf pour la gestion des patients.
 *
 * Règles d'accès :
 *  - Lecture (liste, détail) : tous les rôles cliniques sauf PATIENT
 *  - Création / modification  : ADMIN, MEDECIN, SECRETAIRE, INFIRMIER
 *  - Suppression              : ADMIN uniquement (soft delete)
 */
@Controller
@RequestMapping("/patients")
@RequiredArgsConstructor
public class PatientWebController {

    private final PatientService patientService;
    private final PatientOverviewService patientOverviewService;
    private final UserRepository userRepository;
    private final com.clinic.backend.consultation.ConsultationService consultationService;
    private final com.clinic.backend.lab.LabService labService;
    private final com.clinic.backend.radiology.RadiologyService radiologyService;
    private final com.clinic.backend.hospitalization.HospitalizationService hospitalizationService;
    private final com.clinic.backend.maternity.MaternityService maternityService;
    private final com.clinic.backend.billing.BillingService billingService;

    @GetMapping
    @PreAuthorize("hasAnyRole('MEDECIN','INFIRMIER','SECRETAIRE','PHARMACIEN','LABORANTIN','CAISSIER')")
    public String list(@RequestParam(defaultValue = "") String q,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        Page<Patient> patients = patientService.search(q, page, 20);
        model.addAttribute("patients", patients);
        model.addAttribute("q", q);
        model.addAttribute("currentPage", page);
        return "patients/list";
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MEDECIN','INFIRMIER','SECRETAIRE','PHARMACIEN','LABORANTIN','CAISSIER')")
    public String detail(@PathVariable Long id, Model model) {
        Patient patient = patientService.getByIdWithDoctor(id);

        var consultations = consultationService.findForPatient(id);
        var labRequests = labService.findForPatient(id);
        var radiologyRequests = radiologyService.findForPatient(id);
        var hospitalizations = hospitalizationService.findForPatient(id);
        var invoices = billingService.findForPatient(id);
        // Dossier maternité — pertinent uniquement pour les patientes.
        var maternityRecord = "F".equalsIgnoreCase(patient.getGender())
                ? maternityService.findForPatient(id) : null;

        model.addAttribute("patient", patient);
        model.addAttribute("consultations", consultations);
        model.addAttribute("labRequests", labRequests);
        model.addAttribute("radiologyRequests", radiologyRequests);
        model.addAttribute("hospitalizations", hospitalizations);
        model.addAttribute("invoices", invoices);
        if (maternityRecord != null) {
            model.addAttribute("maternityRecord", maternityRecord);
        }

        // Coup d'œil + timeline (P3.6) — agrégat en mémoire, zéro requête de plus.
        model.addAttribute("overview", patientOverviewService.build(
                patient, consultations, labRequests, radiologyRequests,
                hospitalizations, invoices, maternityRecord));

        return "patients/detail";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAnyRole('MEDECIN','SECRETAIRE','INFIRMIER')")
    public String newForm(Model model) {
        model.addAttribute("patient", new PatientDto());
        model.addAttribute("doctors", userRepository.findAll());
        return "patients/form";
    }

    @PostMapping("/new")
    @PreAuthorize("hasAnyRole('MEDECIN','SECRETAIRE','INFIRMIER')")
    public String create(@ModelAttribute PatientDto dto) {
        Patient created = patientService.create(dto);
        return "redirect:/patients/" + created.getId();
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAnyRole('MEDECIN','SECRETAIRE','INFIRMIER')")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("patient", patientService.toDto(patientService.getById(id)));
        model.addAttribute("doctors", userRepository.findAll());
        return "patients/form";
    }

    @PostMapping("/{id}/edit")
    @PreAuthorize("hasAnyRole('MEDECIN','SECRETAIRE','INFIRMIER')")
    public String update(@PathVariable Long id, @ModelAttribute PatientDto dto) {
        patientService.update(id, dto);
        return "redirect:/patients/" + id;
    }

    @PostMapping("/{id}/photo")
    @PreAuthorize("hasAnyRole('MEDECIN','SECRETAIRE','INFIRMIER')")
    public String uploadPhoto(@PathVariable Long id,
                              @RequestParam("file") MultipartFile file,
                              RedirectAttributes ra) {
        try {
            patientService.uploadPhoto(id, file);
            ra.addFlashAttribute("success", "Photo du patient mise à jour.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/patients/" + id;
    }
}
