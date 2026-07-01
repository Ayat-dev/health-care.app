package com.clinic.backend.controller.web;

import com.clinic.backend.config.RoleProfile;
import com.clinic.backend.model.User;
import com.clinic.backend.security.mfa.MfaGuardInterceptor;
import com.clinic.backend.security.mfa.MfaService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * MFA/2FA en libre-service (Tier E3) : statut, enrôlement TOTP (QR + confirmation), codes de
 * secours, désactivation, et le challenge de second facteur au login. Toute page est réservée
 * à l'utilisateur connecté (aucune donnée d'autrui).
 */
@Controller
@RequestMapping("/mfa")
@RequiredArgsConstructor
public class MfaWebController {

    private final MfaService mfaService;
    private final com.clinic.backend.i18n.WebI18n i18n;

    // ── Statut / gestion ─────────────────────────────────────────────────────────
    @GetMapping
    public String index(@AuthenticationPrincipal User user, Model model) {
        model.addAttribute("enabled", mfaService.isEnabled(user.getId()));
        model.addAttribute("remaining", mfaService.remainingRecoveryCodes(user.getId()));
        return "mfa/index";
    }

    @GetMapping("/setup")
    public String setup(@AuthenticationPrincipal User user, Model model) {
        if (mfaService.isEnabled(user.getId())) return "redirect:/mfa";
        MfaService.SetupData data = mfaService.beginSetup(user.getId());
        model.addAttribute("secret", data.secret());
        model.addAttribute("qr", data.qrDataUri());
        return "mfa/setup";
    }

    @PostMapping("/confirm")
    public String confirm(@AuthenticationPrincipal User user,
                          @RequestParam String code,
                          HttpServletRequest request, RedirectAttributes ra) {
        try {
            var codes = mfaService.confirmSetup(user.getId(), code);
            markVerified(request);           // il vient de prouver un code → pas de re-challenge
            ra.addFlashAttribute("recoveryCodes", codes);
            ra.addFlashAttribute("success", i18n.t("mfa.flash.enabled"));
            return "redirect:/mfa";
        } catch (IllegalArgumentException | IllegalStateException e) {
            ra.addFlashAttribute("error", i18n.t("mfa.flash.bad_code"));
            return "redirect:/mfa/setup";
        }
    }

    @PostMapping("/disable")
    public String disable(@AuthenticationPrincipal User user, RedirectAttributes ra) {
        mfaService.disable(user.getId());
        ra.addFlashAttribute("success", i18n.t("mfa.flash.disabled"));
        return "redirect:/mfa";
    }

    // ── Challenge de second facteur (au login) ─────────────────────────────────────
    @GetMapping("/challenge")
    public String challenge(@AuthenticationPrincipal User user, HttpServletRequest request) {
        if (user == null) return "redirect:/login";
        // Déjà vérifié (ou MFA non activé) → inutile de rechallenger.
        if (!user.isMfaEnabled() || isVerified(request)) {
            return "redirect:" + RoleProfile.fromRole(user.getRole()).homepage;
        }
        return "mfa/challenge";
    }

    @PostMapping("/challenge")
    public String verify(@AuthenticationPrincipal User user,
                         @RequestParam String code,
                         HttpServletRequest request, Model model) {
        if (mfaService.verify(user.getId(), code)) {
            markVerified(request);
            return "redirect:" + RoleProfile.fromRole(user.getRole()).homepage;
        }
        model.addAttribute("error", i18n.t("mfa.flash.bad_code"));
        return "mfa/challenge";
    }

    // ── Helpers session ────────────────────────────────────────────────────────────
    private void markVerified(HttpServletRequest request) {
        request.getSession(true).setAttribute(MfaGuardInterceptor.MFA_VERIFIED, Boolean.TRUE);
    }

    private boolean isVerified(HttpServletRequest request) {
        var session = request.getSession(false);
        return session != null && Boolean.TRUE.equals(session.getAttribute(MfaGuardInterceptor.MFA_VERIFIED));
    }
}
