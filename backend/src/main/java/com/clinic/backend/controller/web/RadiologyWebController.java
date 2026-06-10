package com.clinic.backend.controller.web;

import com.clinic.backend.clinicconfig.ClinicConfigService;
import com.clinic.backend.dto.RadiologyRequestDto;
import com.clinic.backend.model.User;
import com.clinic.backend.patient.Patient;
import com.clinic.backend.patient.PatientService;
import com.clinic.backend.radiology.RadiologyRequest;
import com.clinic.backend.radiology.RadiologyService;
import com.clinic.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;

@Controller
@RequestMapping("/radiology")
@RequiredArgsConstructor
public class RadiologyWebController {

    private final RadiologyService radiologyService;
    private final PatientService patientService;
    private final ClinicConfigService clinicConfigService;
    private final UserRepository userRepository;

    // ── Travail du jour (radiologue) ─────────────────────────────────────────────
    @GetMapping
    public String worklist(Model model) {
        model.addAttribute("requests", radiologyService.worklist());
        return "radiology/worklist";
    }

    // ── Liste complète ───────────────────────────────────────────────────────────
    @GetMapping("/requests")
    public String list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String priority,
                       Model model) {
        List<RadiologyRequest> requests = radiologyService.search(from, to, null, null, status, priority);
        model.addAttribute("requests", requests);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("status", status);
        model.addAttribute("priority", priority);
        return "radiology/list";
    }

    // ── Détail ─────────────────────────────────────────────────────────────────────
    @GetMapping("/requests/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("request", radiologyService.getDtoById(id));
        return "radiology/detail";
    }

    // ── Création ────────────────────────────────────────────────────────────────────
    @GetMapping("/requests/new")
    public String newForm(@RequestParam(required = false) Long consultationId,
                          @RequestParam(required = false) Long patientId,
                          Model model) {
        model.addAttribute("request", radiologyService.prefillFromConsultation(consultationId, patientId));
        populateFormOptions(model);
        return "radiology/form";
    }

    @PostMapping("/requests/new")
    public String create(@ModelAttribute RadiologyRequestDto dto, RedirectAttributes ra, Model model) {
        try {
            RadiologyRequest created = radiologyService.create(dto);
            ra.addFlashAttribute("success", "Demande d'imagerie enregistrée.");
            return "redirect:/radiology/requests/" + created.getId();
        } catch (RuntimeException e) {
            model.addAttribute("request", dto);
            model.addAttribute("error", e.getMessage());
            populateFormOptions(model);
            return "radiology/form";
        }
    }

    // ── Compte-rendu (radiologue) ─────────────────────────────────────────────────────
    @GetMapping("/requests/{id}/report")
    public String reportForm(@PathVariable Long id, Model model) {
        model.addAttribute("request", radiologyService.getDtoById(id));
        return "radiology/report-form";
    }

    @PostMapping("/requests/{id}/report")
    public String saveReport(@PathVariable Long id, @ModelAttribute RadiologyRequestDto form,
                             RedirectAttributes ra, Model model) {
        try {
            radiologyService.saveReport(id, form.getFindings(), form.getConclusion());
            ra.addFlashAttribute("success", "Compte-rendu enregistré.");
            return "redirect:/radiology/requests/" + id;
        } catch (RuntimeException e) {
            model.addAttribute("request", radiologyService.getDtoById(id));
            model.addAttribute("error", e.getMessage());
            return "radiology/report-form";
        }
    }

    // ── Images ──────────────────────────────────────────────────────────────────────────
    @PostMapping("/requests/{id}/images")
    public String uploadImage(@PathVariable Long id,
                              @RequestParam("file") MultipartFile file,
                              @RequestParam(required = false) String caption,
                              RedirectAttributes ra) {
        try {
            radiologyService.addImage(id, file, caption);
            ra.addFlashAttribute("success", "Image ajoutée.");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/radiology/requests/" + id + "/report";
    }

    @PostMapping("/requests/{id}/images/{imageId}/delete")
    public String deleteImage(@PathVariable Long id, @PathVariable Long imageId, RedirectAttributes ra) {
        try {
            radiologyService.deleteImage(id, imageId);
            ra.addFlashAttribute("success", "Image supprimée.");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/radiology/requests/" + id + "/report";
    }

    // ── Actions de statut ───────────────────────────────────────────────────────────
    @PostMapping("/requests/{id}/validate")
    public String validate(@PathVariable Long id, RedirectAttributes ra) {
        try {
            radiologyService.validate(id);
            ra.addFlashAttribute("success", "Compte-rendu validé.");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/radiology/requests/" + id;
    }

    @PostMapping("/requests/{id}/deliver")
    public String deliver(@PathVariable Long id, RedirectAttributes ra) {
        try {
            radiologyService.deliver(id);
            ra.addFlashAttribute("success", "Compte-rendu marqué comme livré.");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/radiology/requests/" + id;
    }

    @PostMapping("/requests/{id}/cancel")
    public String cancel(@PathVariable Long id, RedirectAttributes ra) {
        try {
            radiologyService.cancel(id);
            ra.addFlashAttribute("success", "Demande annulée.");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/radiology/requests/" + id;
    }

    // ── Compte-rendu imprimable ───────────────────────────────────────────────────────────
    @GetMapping("/requests/{id}/bulletin")
    public String bulletin(@PathVariable Long id, Model model) {
        RadiologyRequestDto request = radiologyService.getDtoById(id);
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
        return "radiology/bulletin";
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────
    private void populateFormOptions(Model model) {
        model.addAttribute("patients", patientService.search("", 0, 500).getContent());
        model.addAttribute("doctors", doctors());
        model.addAttribute("exams", radiologyService.listCatalog());
    }

    private List<User> doctors() {
        return userRepository.findByRoleAndDeletedAtIsNullOrderByFullNameAsc("MEDECIN");
    }
}
