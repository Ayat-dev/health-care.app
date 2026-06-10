package com.clinic.backend.controller.web;

import com.clinic.backend.dto.DispensationDto;
import com.clinic.backend.dto.DispensationItemDto;
import com.clinic.backend.dto.DrugDto;
import com.clinic.backend.dto.StockItemDto;
import com.clinic.backend.pharmacy.Dispensation;
import com.clinic.backend.pharmacy.PharmacyService;
import com.clinic.backend.patient.PatientService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
@RequestMapping("/pharmacy")
@RequiredArgsConstructor
public class PharmacyWebController {

    private final PharmacyService pharmacyService;
    private final PatientService patientService;

    // ── Tableau de bord ────────────────────────────────────────────────────────
    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("dash", pharmacyService.dashboard());
        return "pharmacy/dashboard";
    }

    // ── Catalogue médicaments ───────────────────────────────────────────────────
    @GetMapping("/drugs")
    public String drugs(@RequestParam(required = false) String q,
                        @RequestParam(required = false) String category,
                        Model model) {
        model.addAttribute("drugs", pharmacyService.listDrugs(q, category));
        model.addAttribute("categories", pharmacyService.drugCategories());
        model.addAttribute("q", q);
        model.addAttribute("category", category);
        return "pharmacy/drugs/list";
    }

    @GetMapping("/drugs/new")
    @PreAuthorize("hasAnyRole('PHARMACIEN','ADMIN')")
    public String newDrug(Model model) {
        model.addAttribute("drug", new DrugDto());
        return "pharmacy/drugs/form";
    }

    @PostMapping("/drugs/new")
    @PreAuthorize("hasAnyRole('PHARMACIEN','ADMIN')")
    public String createDrug(@ModelAttribute("drug") DrugDto dto, Model model) {
        try {
            pharmacyService.createDrug(dto);
            return "redirect:/pharmacy/drugs";
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
            return "pharmacy/drugs/form";
        }
    }

    @GetMapping("/drugs/{id}/edit")
    @PreAuthorize("hasAnyRole('PHARMACIEN','ADMIN')")
    public String editDrug(@PathVariable Long id, Model model) {
        model.addAttribute("drug", pharmacyService.getDrugDto(id));
        return "pharmacy/drugs/form";
    }

    @PostMapping("/drugs/{id}/edit")
    @PreAuthorize("hasAnyRole('PHARMACIEN','ADMIN')")
    public String updateDrug(@PathVariable Long id, @ModelAttribute("drug") DrugDto dto, Model model) {
        try {
            pharmacyService.updateDrug(id, dto);
            return "redirect:/pharmacy/drugs";
        } catch (RuntimeException e) {
            dto.setId(id);
            model.addAttribute("error", e.getMessage());
            return "pharmacy/drugs/form";
        }
    }

    @PostMapping("/drugs/{id}/toggle")
    @PreAuthorize("hasAnyRole('PHARMACIEN','ADMIN')")
    public String toggleDrug(@PathVariable Long id, RedirectAttributes ra) {
        try {
            pharmacyService.toggleDrug(id);
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/pharmacy/drugs";
    }

    // ── Stock ───────────────────────────────────────────────────────────────────
    @GetMapping("/stock")
    public String stock(Model model) {
        model.addAttribute("stock", pharmacyService.listStock());
        return "pharmacy/stock";
    }

    @GetMapping("/stock/receive")
    @PreAuthorize("hasAnyRole('PHARMACIEN','ADMIN')")
    public String receiveForm(@RequestParam(required = false) Long drugId, Model model) {
        StockItemDto dto = new StockItemDto();
        dto.setDrugId(drugId);
        dto.setReceivedAt(LocalDate.now());
        model.addAttribute("item", dto);
        model.addAttribute("drugs", pharmacyService.listActiveDrugs());
        return "pharmacy/stock-receive";
    }

    @PostMapping("/stock/receive")
    @PreAuthorize("hasAnyRole('PHARMACIEN','ADMIN')")
    public String receive(@ModelAttribute("item") StockItemDto dto, RedirectAttributes ra, Model model) {
        try {
            pharmacyService.receiveStock(dto);
            ra.addFlashAttribute("success", "Lot réceptionné et ajouté au stock.");
            return "redirect:/pharmacy/stock";
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("drugs", pharmacyService.listActiveDrugs());
            return "pharmacy/stock-receive";
        }
    }

    // ── Dispensations ───────────────────────────────────────────────────────────
    @GetMapping("/dispensations")
    public String dispensations(Model model) {
        model.addAttribute("dispensations", pharmacyService.listDispensations());
        return "pharmacy/dispensations/list";
    }

    @GetMapping("/dispensations/new")
    @PreAuthorize("hasAnyRole('PHARMACIEN','ADMIN')")
    public String dispenseForm(@RequestParam(required = false) Long prescriptionId,
                               @RequestParam(required = false) Long patientId,
                               RedirectAttributes ra, Model model) {
        DispensationDto dto;
        if (prescriptionId != null) {
            try {
                dto = pharmacyService.prefillFromPrescription(prescriptionId);
            } catch (RuntimeException e) {
                ra.addFlashAttribute("error", e.getMessage());
                return "redirect:/pharmacy/dispensations";
            }
        } else {
            dto = new DispensationDto();
            dto.setPatientId(patientId);
        }
        // Quelques lignes vides pour la saisie libre
        for (int i = 0; i < 4; i++) dto.getItems().add(new DispensationItemDto());
        prepareDispenseForm(model, dto);
        return "pharmacy/dispensations/form";
    }

    @PostMapping("/dispensations/new")
    @PreAuthorize("hasAnyRole('PHARMACIEN','ADMIN')")
    public String dispense(@ModelAttribute("dispensation") DispensationDto dto,
                           RedirectAttributes ra, Model model) {
        try {
            Dispensation d = pharmacyService.dispense(dto);
            ra.addFlashAttribute("success", "Dispensation enregistrée.");
            return "redirect:/pharmacy/dispensations/" + d.getId();
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
            prepareDispenseForm(model, dto);
            return "pharmacy/dispensations/form";
        }
    }

    @GetMapping("/dispensations/{id}")
    public String dispensationDetail(@PathVariable Long id, Model model) {
        model.addAttribute("dispensation", pharmacyService.getDispensationDto(id));
        return "pharmacy/dispensations/detail";
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────
    private void prepareDispenseForm(Model model, DispensationDto dto) {
        model.addAttribute("dispensation", dto);
        model.addAttribute("drugs", pharmacyService.listActiveDrugs());
        model.addAttribute("patients", patientService.search("", 0, 500).getContent());
    }
}
