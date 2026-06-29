package com.clinic.backend.controller.web;

import com.clinic.backend.dto.ClinicDto;
import com.clinic.backend.dto.UserDto;
import com.clinic.backend.service.UserService;
import com.clinic.backend.tenant.ClinicService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Administration du registre des cliniques (P4.2) — réservée au SUPER_ADMIN.
 * C'est le seul module transverse (non tenant-scopé) de la sidebar.
 */
@Controller
@RequestMapping("/admin/clinics")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminClinicWebController {

    private final ClinicService clinicService;
    private final UserService userService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("clinics", clinicService.listAll());
        return "admin/clinics/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("clinic", new ClinicDto());
        return "admin/clinics/form";
    }

    @PostMapping("/new")
    public String create(@ModelAttribute("clinic") ClinicDto dto, Model model) {
        try {
            clinicService.create(dto);
            return "redirect:/admin/clinics";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "admin/clinics/form";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("clinic", clinicService.toDto(clinicService.getById(id)));
        return "admin/clinics/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @ModelAttribute("clinic") ClinicDto dto, Model model) {
        try {
            clinicService.update(id, dto);
            return "redirect:/admin/clinics";
        } catch (IllegalArgumentException e) {
            dto.setId(id);
            model.addAttribute("error", e.getMessage());
            return "admin/clinics/form";
        }
    }

    // ── Provisionnement du premier admin d'une clinique (multi-tenant P4.2) ──
    @GetMapping("/{id}/admin")
    public String newAdminForm(@PathVariable Long id, Model model) {
        model.addAttribute("clinic", clinicService.toDto(clinicService.getById(id)));
        model.addAttribute("user", new UserDto());
        return "admin/clinics/admin-form";
    }

    @PostMapping("/{id}/admin")
    public String createAdmin(@PathVariable Long id, @ModelAttribute("user") UserDto dto,
                              RedirectAttributes ra, Model model) {
        try {
            userService.createForClinic(id, dto);
            ra.addFlashAttribute("success", "admin.clinics.admin_created");
            return "redirect:/admin/clinics";
        } catch (IllegalArgumentException e) {
            model.addAttribute("clinic", clinicService.toDto(clinicService.getById(id)));
            model.addAttribute("user", dto);
            model.addAttribute("error", e.getMessage());
            return "admin/clinics/admin-form";
        }
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id, RedirectAttributes ra) {
        try {
            clinicService.toggleActive(id);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/clinics";
    }
}
