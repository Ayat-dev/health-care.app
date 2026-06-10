package com.clinic.backend.controller.web;

import com.clinic.backend.catalog.LabTestCatalogService;
import com.clinic.backend.clinicconfig.ClinicConfigService;
import com.clinic.backend.dto.LabRequestDto;
import com.clinic.backend.lab.LabRequest;
import com.clinic.backend.lab.LabService;
import com.clinic.backend.model.User;
import com.clinic.backend.patient.Patient;
import com.clinic.backend.patient.PatientService;
import com.clinic.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;

@Controller
@RequestMapping("/lab")
@RequiredArgsConstructor
public class LabWebController {

    private final LabService labService;
    private final PatientService patientService;
    private final LabTestCatalogService labTestCatalogService;
    private final ClinicConfigService clinicConfigService;
    private final UserRepository userRepository;

    // ── Travail du jour (laborantin) ─────────────────────────────────────────────
    @GetMapping
    public String worklist(Model model) {
        model.addAttribute("requests", labService.worklist());
        return "lab/worklist";
    }

    // ── Liste complète ───────────────────────────────────────────────────────────
    @GetMapping("/requests")
    public String list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String priority,
                       Model model) {
        List<LabRequest> requests = labService.search(from, to, null, null, status, priority);
        model.addAttribute("requests", requests);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("status", status);
        model.addAttribute("priority", priority);
        return "lab/list";
    }

    // ── Détail ─────────────────────────────────────────────────────────────────────
    @GetMapping("/requests/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("request", labService.getDtoById(id));
        return "lab/detail";
    }

    // ── Création ────────────────────────────────────────────────────────────────────
    @GetMapping("/requests/new")
    public String newForm(@RequestParam(required = false) Long consultationId,
                          @RequestParam(required = false) Long patientId,
                          Model model) {
        model.addAttribute("request", labService.prefillFromConsultation(consultationId, patientId));
        populateFormOptions(model);
        return "lab/form";
    }

    @PostMapping("/requests/new")
    public String create(@ModelAttribute LabRequestDto dto, RedirectAttributes ra, Model model) {
        try {
            LabRequest created = labService.create(dto);
            ra.addFlashAttribute("success", "Demande d'analyses enregistrée.");
            return "redirect:/lab/requests/" + created.getId();
        } catch (RuntimeException e) {
            model.addAttribute("request", dto);
            model.addAttribute("error", e.getMessage());
            populateFormOptions(model);
            return "lab/form";
        }
    }

    // ── Saisie des résultats (laborantin) ─────────────────────────────────────────────
    @GetMapping("/requests/{id}/results")
    public String resultForm(@PathVariable Long id, Model model) {
        model.addAttribute("request", labService.getDtoById(id));
        return "lab/result-entry";
    }

    @PostMapping("/requests/{id}/results")
    public String saveResults(@PathVariable Long id, @ModelAttribute LabRequestDto form,
                              RedirectAttributes ra, Model model) {
        try {
            labService.enterResults(id, form.getItems());
            ra.addFlashAttribute("success", "Résultats enregistrés.");
            return "redirect:/lab/requests/" + id;
        } catch (RuntimeException e) {
            model.addAttribute("request", labService.getDtoById(id));
            model.addAttribute("error", e.getMessage());
            return "lab/result-entry";
        }
    }

    // ── Actions de statut ───────────────────────────────────────────────────────────
    @PostMapping("/requests/{id}/validate")
    public String validate(@PathVariable Long id, RedirectAttributes ra) {
        try {
            labService.validate(id);
            ra.addFlashAttribute("success", "Résultats validés.");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/lab/requests/" + id;
    }

    @PostMapping("/requests/{id}/deliver")
    public String deliver(@PathVariable Long id, RedirectAttributes ra) {
        try {
            labService.deliver(id);
            ra.addFlashAttribute("success", "Bulletin marqué comme livré.");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/lab/requests/" + id;
    }

    @PostMapping("/requests/{id}/cancel")
    public String cancel(@PathVariable Long id, RedirectAttributes ra) {
        try {
            labService.cancel(id);
            ra.addFlashAttribute("success", "Demande annulée.");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/lab/requests/" + id;
    }

    // ── Bulletin imprimable ───────────────────────────────────────────────────────────
    @GetMapping("/requests/{id}/bulletin")
    public String bulletin(@PathVariable Long id, Model model) {
        LabRequestDto request = labService.getDtoById(id);
        model.addAttribute("request", request);
        model.addAttribute("config", clinicConfigService.getConfig());

        Integer age = null;
        if (request.getPatientId() != null) {
            Patient patient = patientService.getById(request.getPatientId());
            if (patient.getBirthDate() != null) {
                age = Period.between(patient.getBirthDate(), LocalDate.now()).getYears();
            }
        }
        model.addAttribute("patientAge", age);
        return "lab/bulletin";
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────
    private void populateFormOptions(Model model) {
        model.addAttribute("patients", patientService.search("", 0, 500).getContent());
        model.addAttribute("doctors", doctors());
        model.addAttribute("tests", labTestCatalogService.listActive());
    }

    private List<User> doctors() {
        return userRepository.findByRoleAndDeletedAtIsNullOrderByFullNameAsc("MEDECIN");
    }
}
