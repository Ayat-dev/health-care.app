package com.clinic.backend.controller.web;

import com.clinic.backend.setup.SetupForm;
import com.clinic.backend.setup.SetupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Assistant de première installation ({@code /setup}). Accessible sans authentification
 * (whitelist dans {@code SecurityConfig}) mais <b>uniquement tant que l'application
 * n'est pas installée</b> : une fois un administrateur créé, l'assistant se verrouille
 * et renvoie vers {@code /login}.
 */
@Controller
@RequiredArgsConstructor
public class SetupWebController {

    private final SetupService setupService;

    @GetMapping("/setup")
    public String show(Model model) {
        if (!setupService.isSetupRequired()) {
            return "redirect:/login";
        }
        if (!model.containsAttribute("setupForm")) {
            model.addAttribute("setupForm", new SetupForm());
        }
        return "setup/wizard";
    }

    @PostMapping("/setup")
    public String submit(@ModelAttribute("setupForm") SetupForm form, Model model) {
        if (!setupService.isSetupRequired()) {
            return "redirect:/login";
        }
        try {
            setupService.complete(form);
            return "redirect:/login?setup=success";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            model.addAttribute("error", ex.getMessage());
            return "setup/wizard";
        }
    }
}
