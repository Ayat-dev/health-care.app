package com.clinic.backend.controller.web;

import com.clinic.backend.catalog.ActCatalogService;
import com.clinic.backend.department.DepartmentService;
import com.clinic.backend.dto.ActCatalogDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/acts")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class ActCatalogWebController {

    private final ActCatalogService actCatalogService;
    private final DepartmentService departmentService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("acts", actCatalogService.listAllAsDto());
        return "admin/acts/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("act", new ActCatalogDto());
        model.addAttribute("departments", departmentService.listActive());
        return "admin/acts/form";
    }

    @PostMapping("/new")
    public String create(@ModelAttribute("act") ActCatalogDto dto, Model model) {
        try {
            actCatalogService.create(dto);
            return "redirect:/admin/acts";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("departments", departmentService.listActive());
            return "admin/acts/form";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("act", actCatalogService.getDtoById(id));
        model.addAttribute("departments", departmentService.listActive());
        return "admin/acts/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @ModelAttribute("act") ActCatalogDto dto, Model model) {
        try {
            actCatalogService.update(id, dto);
            return "redirect:/admin/acts";
        } catch (IllegalArgumentException e) {
            dto.setId(id);
            model.addAttribute("error", e.getMessage());
            model.addAttribute("departments", departmentService.listActive());
            return "admin/acts/form";
        }
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id, RedirectAttributes ra) {
        try {
            actCatalogService.toggleActive(id);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/acts";
    }
}
