package com.clinic.backend.controller.web;

import com.clinic.backend.config.Module;
import com.clinic.backend.config.RoleProfile;
import com.clinic.backend.reports.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Contrôleur web pour login + tableau de bord d'accueil.
 *
 * Le tableau de bord est <b>spécifique au rôle</b> :
 * <ul>
 *   <li>ADMIN (et tout rôle portant le module {@code DASHBOARD}) → KPIs d'accueil (réels)</li>
 *   <li>MEDECIN → tableau de bord médecin dédié (sa journée : consultations, RDV, labos à valider)</li>
 *   <li>Autres rôles → redirigés vers leur page d'accueil métier (pas de tableau de bord)</li>
 * </ul>
 */
@Controller
@RequiredArgsConstructor
public class AuthWebController {

    private final ReportService reportService;

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    /** Racine : on renvoie chaque rôle vers sa page d'accueil (jamais le dashboard admin par défaut). */
    @GetMapping({"", "/"})
    public String root(Authentication auth) {
        return "redirect:" + RoleProfile.fromRole(role(auth)).homepage;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, Authentication auth) {
        String role = role(auth);

        // Médecin : tableau de bord dédié (sa journée).
        if ("MEDECIN".equals(role)) {
            model.addAttribute("dashboard", reportService.doctorDashboard());
            return "dashboard-doctor";
        }

        // Rôles sans module Tableau de bord (caissier, infirmier, secrétaire, pharmacien…)
        // → pas de KPIs de direction : on les renvoie vers leur page d'accueil métier.
        RoleProfile profile = RoleProfile.fromRole(role);
        if (!profile.modules.contains(Module.DASHBOARD)) {
            return "redirect:" + profile.homepage;
        }

        // ADMIN : KPIs d'accueil réels.
        ReportService.LandingSummary s = reportService.landingSummary();
        model.addAttribute("patientsCount", s.patients());
        model.addAttribute("appointmentsToday", s.appointmentsToday());
        model.addAttribute("consultationsToday", s.consultationsToday());
        model.addAttribute("pendingInvoices", s.pendingInvoices());
        return "dashboard";
    }

    /** Rôle de l'utilisateur courant (ex. "CAISSIER"), null si non authentifié. */
    private String role(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) return null;
        return auth.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse(null);
    }
}
