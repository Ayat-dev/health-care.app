package com.clinic.backend.controller.web;

import com.clinic.backend.dto.InsuranceProviderDto;
import com.clinic.backend.insurance.InsuranceProviderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/insurance")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class InsuranceProviderWebController {

    private final InsuranceProviderService insuranceProviderService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("providers", insuranceProviderService.listAll());
        return "admin/insurance/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("provider", new InsuranceProviderDto());
        return "admin/insurance/form";
    }

    @PostMapping("/new")
    public String create(@ModelAttribute("provider") InsuranceProviderDto dto, Model model) {
        try {
            insuranceProviderService.create(dto);
            return "redirect:/admin/insurance";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "admin/insurance/form";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("provider", insuranceProviderService.toDto(insuranceProviderService.getById(id)));
        return "admin/insurance/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @ModelAttribute("provider") InsuranceProviderDto dto, Model model) {
        try {
            insuranceProviderService.update(id, dto);
            return "redirect:/admin/insurance";
        } catch (IllegalArgumentException e) {
            dto.setId(id);
            model.addAttribute("error", e.getMessage());
            return "admin/insurance/form";
        }
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id, RedirectAttributes ra) {
        try {
            insuranceProviderService.toggleActive(id);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/insurance";
    }
}
