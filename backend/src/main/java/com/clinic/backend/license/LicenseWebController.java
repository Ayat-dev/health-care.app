package com.clinic.backend.license;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Page de licence : état courant (essai / active / expirée) et activation d'une clé.
 * <p>
 * Accessible en lecture à tout utilisateur authentifié (comprendre pourquoi l'app est
 * en lecture seule) ; l'activation est réservée aux rôles de gestion. La page reste
 * atteignable même en état bloqué (exclue du {@link LicenseGuardInterceptor}).
 */
@Controller
@RequestMapping("/license")
@RequiredArgsConstructor
public class LicenseWebController {

    private final LicenseService licenseService;

    @GetMapping
    public String page(@RequestParam(name = "blocked", required = false) String blocked, Model model) {
        model.addAttribute("state", licenseService.currentState());
        model.addAttribute("blockedNotice", blocked != null);
        return "license/activate";
    }

    @PostMapping("/activate")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','SUPER_ADMIN')")
    public String activate(@RequestParam("licenseKey") String licenseKey, RedirectAttributes ra) {
        try {
            LicenseState state = licenseService.activate(licenseKey);
            ra.addFlashAttribute("success",
                    "Licence activée (" + state.edition() + ") — valide jusqu'au " + state.validUntil() + ".");
        } catch (LicenseException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/license";
    }
}
