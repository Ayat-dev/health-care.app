package com.clinic.backend.controller.web;

import com.clinic.backend.catalog.LabTestCatalogService;
import com.clinic.backend.dto.LabTestCatalogDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/lab-tests")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class LabTestCatalogWebController {

    private final LabTestCatalogService labTestCatalogService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("tests", labTestCatalogService.listAll());
        return "admin/lab-tests/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("test", new LabTestCatalogDto());
        return "admin/lab-tests/form";
    }

    @PostMapping("/new")
    public String create(@ModelAttribute("test") LabTestCatalogDto dto, Model model) {
        try {
            labTestCatalogService.create(dto);
            return "redirect:/admin/lab-tests";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "admin/lab-tests/form";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("test", labTestCatalogService.toDto(labTestCatalogService.getById(id)));
        return "admin/lab-tests/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @ModelAttribute("test") LabTestCatalogDto dto, Model model) {
        try {
            labTestCatalogService.update(id, dto);
            return "redirect:/admin/lab-tests";
        } catch (IllegalArgumentException e) {
            dto.setId(id);
            model.addAttribute("error", e.getMessage());
            return "admin/lab-tests/form";
        }
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id, RedirectAttributes ra) {
        try {
            labTestCatalogService.toggleActive(id);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/lab-tests";
    }
}
