package com.clinic.backend.controller.web;

import com.clinic.backend.i18n.WebI18n;
import com.clinic.backend.model.User;
import com.clinic.backend.security.mfa.MfaService;
import com.clinic.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * « Mon compte » en libre-service pour le personnel (tout rôle sauf PATIENT, qui a
 * son propre {@code /portal/profile}). Regroupe l'identité en lecture seule, le
 * changement de mot de passe self-service (réutilise {@link UserService#changeOwnPassword}),
 * et un raccourci vers la gestion MFA ({@code /mfa}). Aucune donnée d'autrui.
 */
@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated() and !hasRole('PATIENT')")
public class ProfileWebController {

    private final UserService userService;
    private final MfaService mfaService;
    private final WebI18n i18n;

    @GetMapping
    public String profile(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("account", user);
        model.addAttribute("mfaEnabled", mfaService.isEnabled(user.getId()));
        return "profile/index";
    }

    @PostMapping("/password")
    public String changePassword(@AuthenticationPrincipal User user,
                                 @RequestParam String currentPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 RedirectAttributes ra) {
        if (newPassword == null || !newPassword.equals(confirmPassword)) {
            ra.addFlashAttribute("error", i18n.t("profile.flash.password_mismatch"));
            return "redirect:/profile";
        }
        try {
            userService.changeOwnPassword(user.getId(), currentPassword, newPassword);
            ra.addFlashAttribute("success", i18n.t("profile.flash.password_changed"));
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/profile";
    }
}
