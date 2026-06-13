package com.clinic.backend.controller.web;

import com.clinic.backend.dto.MaternityRecordDto;
import com.clinic.backend.dto.PrenatalVisitDto;
import com.clinic.backend.maternity.MaternityRecord;
import com.clinic.backend.maternity.MaternityService;
import com.clinic.backend.model.User;
import com.clinic.backend.patient.Patient;
import com.clinic.backend.patient.PatientService;
import com.clinic.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/maternity")
@RequiredArgsConstructor
public class MaternityWebController {

    private final MaternityService maternityService;
    private final PatientService patientService;
    private final UserRepository userRepository;

    // ── Liste des grossesses ─────────────────────────────────────────────────────
    @GetMapping
    public String list(@RequestParam(required = false) String status, Model model) {
        model.addAttribute("records", maternityService.search(status));
        model.addAttribute("status", status);
        return "maternity/list";
    }

    // ── Ouverture d'un dossier ────────────────────────────────────────────────────
    @GetMapping("/new")
    public String newForm(@RequestParam(required = false) Long patientId, Model model) {
        model.addAttribute("record", maternityService.prefillForPatient(patientId));
        populateFormOptions(model);
        return "maternity/form";
    }

    @PostMapping("/new")
    public String open(@ModelAttribute MaternityRecordDto dto, RedirectAttributes ra, Model model) {
        try {
            MaternityRecord created = maternityService.openRecord(dto);
            ra.addFlashAttribute("success", "Dossier maternité ouvert.");
            return "redirect:/maternity/" + created.getId();
        } catch (RuntimeException e) {
            model.addAttribute("record", dto);
            model.addAttribute("error", e.getMessage());
            populateFormOptions(model);
            return "maternity/form";
        }
    }

    // ── Dossier (onglets) ──────────────────────────────────────────────────────────
    @GetMapping("/{id}")
    public String record(@PathVariable Long id, Model model) {
        model.addAttribute("record", maternityService.getDtoById(id));
        return "maternity/record";
    }

    // ── Modification des données de base ───────────────────────────────────────────
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("record", maternityService.getDtoById(id));
        populateFormOptions(model);
        return "maternity/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @ModelAttribute MaternityRecordDto dto,
                         RedirectAttributes ra, Model model) {
        try {
            maternityService.update(id, dto);
            ra.addFlashAttribute("success", "Dossier mis à jour.");
            return "redirect:/maternity/" + id;
        } catch (RuntimeException e) {
            dto.setId(id);
            model.addAttribute("record", dto);
            model.addAttribute("error", e.getMessage());
            populateFormOptions(model);
            return "maternity/form";
        }
    }

    // ── Consultations prénatales (CPN) ─────────────────────────────────────────────
    @GetMapping("/{id}/visits/new")
    public String newVisitForm(@PathVariable Long id, Model model) {
        model.addAttribute("record", maternityService.getDtoById(id));
        model.addAttribute("visit", maternityService.prefillVisit(id));
        model.addAttribute("doctors", doctors());
        return "maternity/visit-form";
    }

    @PostMapping("/{id}/visits/new")
    public String addVisit(@PathVariable Long id, @ModelAttribute PrenatalVisitDto dto,
                           RedirectAttributes ra, Model model) {
        try {
            maternityService.addVisit(id, dto);
            ra.addFlashAttribute("success", "Consultation prénatale enregistrée.");
            return "redirect:/maternity/" + id;
        } catch (RuntimeException e) {
            model.addAttribute("record", maternityService.getDtoById(id));
            model.addAttribute("visit", dto);
            model.addAttribute("error", e.getMessage());
            model.addAttribute("doctors", doctors());
            return "maternity/visit-form";
        }
    }

    @GetMapping("/{id}/visits/{visitId}/edit")
    public String editVisitForm(@PathVariable Long id, @PathVariable Long visitId, Model model) {
        model.addAttribute("record", maternityService.getDtoById(id));
        model.addAttribute("visit", maternityService.getVisitDto(visitId));
        model.addAttribute("doctors", doctors());
        return "maternity/visit-form";
    }

    @PostMapping("/{id}/visits/{visitId}/edit")
    public String updateVisit(@PathVariable Long id, @PathVariable Long visitId,
                              @ModelAttribute PrenatalVisitDto dto, RedirectAttributes ra, Model model) {
        try {
            maternityService.updateVisit(visitId, dto);
            ra.addFlashAttribute("success", "Consultation prénatale mise à jour.");
            return "redirect:/maternity/" + id;
        } catch (RuntimeException e) {
            dto.setId(visitId);
            model.addAttribute("record", maternityService.getDtoById(id));
            model.addAttribute("visit", dto);
            model.addAttribute("error", e.getMessage());
            model.addAttribute("doctors", doctors());
            return "maternity/visit-form";
        }
    }

    // ── Accouchement ────────────────────────────────────────────────────────────────
    @GetMapping("/{id}/delivery")
    public String deliveryForm(@PathVariable Long id, Model model) {
        model.addAttribute("record", maternityService.getDtoById(id));
        return "maternity/delivery-form";
    }

    @PostMapping("/{id}/delivery")
    public String recordDelivery(@PathVariable Long id, @ModelAttribute MaternityRecordDto dto,
                                 RedirectAttributes ra, Model model) {
        try {
            maternityService.recordDelivery(id, dto);
            ra.addFlashAttribute("success", "Accouchement enregistré.");
            return "redirect:/maternity/" + id;
        } catch (RuntimeException e) {
            model.addAttribute("record", maternityService.getDtoById(id));
            model.addAttribute("error", e.getMessage());
            return "maternity/delivery-form";
        }
    }

    @PostMapping("/{id}/close")
    public String close(@PathVariable Long id, RedirectAttributes ra) {
        try {
            maternityService.close(id);
            ra.addFlashAttribute("success", "Dossier clôturé.");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/maternity/" + id;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────
    private void populateFormOptions(Model model) {
        List<Patient> women = patientService.search("", 0, 500).getContent().stream()
                .filter(p -> "F".equalsIgnoreCase(p.getGender()))
                .toList();
        model.addAttribute("patients", women);
        model.addAttribute("doctors", doctors());
    }

    private List<User> doctors() {
        return userRepository.findByRoleAndDeletedAtIsNullOrderByFullNameAsc("MEDECIN");
    }
}
