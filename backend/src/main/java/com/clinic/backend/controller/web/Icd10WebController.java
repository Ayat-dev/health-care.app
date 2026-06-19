package com.clinic.backend.controller.web;

import com.clinic.backend.catalog.Icd10Service;
import com.clinic.backend.dto.Icd10CodeDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/icd10")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class Icd10WebController {

    private final Icd10Service icd10Service;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("codes", icd10Service.listAll());
        return "admin/icd10/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("code", new Icd10CodeDto());
        return "admin/icd10/form";
    }

    @PostMapping("/new")
    public String create(@ModelAttribute("code") Icd10CodeDto dto, Model model) {
        try {
            icd10Service.create(dto);
            return "redirect:/admin/icd10";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "admin/icd10/form";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("code", icd10Service.toDto(icd10Service.getById(id)));
        return "admin/icd10/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @ModelAttribute("code") Icd10CodeDto dto, Model model) {
        try {
            icd10Service.update(id, dto);
            return "redirect:/admin/icd10";
        } catch (IllegalArgumentException e) {
            dto.setId(id);
            model.addAttribute("error", e.getMessage());
            return "admin/icd10/form";
        }
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id, RedirectAttributes ra) {
        try {
            icd10Service.toggleActive(id);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/icd10";
    }
}
