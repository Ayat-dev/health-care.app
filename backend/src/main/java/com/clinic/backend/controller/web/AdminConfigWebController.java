package com.clinic.backend.controller.web;

import com.clinic.backend.clinicconfig.ClinicConfigService;
import com.clinic.backend.dto.ClinicConfigDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/config")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminConfigWebController {

    private final ClinicConfigService clinicConfigService;

    @GetMapping
    public String view(Model model) {
        model.addAttribute("config", clinicConfigService.toDto(clinicConfigService.getConfig()));
        return "admin/config/form";
    }

    @PostMapping
    public String save(@ModelAttribute("config") ClinicConfigDto dto,
                       Model model, RedirectAttributes ra) {
        try {
            clinicConfigService.update(dto);
            ra.addFlashAttribute("success", "Configuration enregistrée.");
            return "redirect:/admin/config";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "admin/config/form";
        }
    }
}
