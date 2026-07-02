package com.clinic.backend.controller.web;

import com.clinic.backend.clinicconfig.ClinicConfigService;
import com.clinic.backend.reports.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;

@Controller
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportWebController {

    private final ReportService reportService;
    private final ClinicConfigService clinicConfigService;
    private final com.clinic.backend.export.ReportExportService reportExportService;

    // ── Hub Rapports ─────────────────────────────────────────────────────────────
    // Point d'entrée du lien « Rapports » de la sidebar. Chaque rôle est dirigé vers
    // le rapport qu'il a le droit de consulter (évite un 403 sur la page de direction
    // pour le caissier/secrétaire qui ont le module REPORTS mais pas le dashboard direction).
    @GetMapping({"", "/dashboard"})
    public String dashboard(@RequestParam(required = false) Integer month,
                            @RequestParam(required = false) Integer year,
                            Model model, org.springframework.security.core.Authentication auth) {
        // Cockpit financier (revenu, encaissé jour/mois) = pilotage business → OWNER seul.
        // L'ADMIN (technique) n'y a plus accès ; le MEDECIN non plus (P6, fuite financière colmatée).
        if (hasAnyRole(auth, "OWNER")) {
            LocalDate now = LocalDate.now();
            int m = month != null ? month : now.getMonthValue();
            int y = year != null ? year : now.getYear();
            model.addAttribute("dashboard", reportService.adminDashboard(m, y));
            model.addAttribute("config", clinicConfigService.getConfig());
            addPeriod(model, m, y);
            return "reports/dashboard";
        }
        // MEDECIN : jamais de chiffre financier → renvoyé vers ses rapports cliniques.
        if (hasAnyRole(auth, "MEDECIN")) return "redirect:/reports/activity";
        if (hasAnyRole(auth, "CAISSIER")) return "redirect:/reports/financial";
        if (hasAnyRole(auth, "SECRETAIRE")) return "redirect:/reports/outstanding";
        // Tout autre rôle atterrissant ici (pas de rapport accessible) → page d'accueil.
        return "redirect:/";
    }

    private static boolean hasAnyRole(org.springframework.security.core.Authentication auth, String... roles) {
        if (auth == null) return false;
        for (String r : roles) {
            String authority = "ROLE_" + r;
            if (auth.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()))) {
                return true;
            }
        }
        return false;
    }

    // ── Bilan financier mensuel ─────────────────────────────────────────────────
    @GetMapping("/financial")
    @PreAuthorize("hasAnyRole('OWNER','CAISSIER')")
    public String financial(@RequestParam(required = false) Integer month,
                            @RequestParam(required = false) Integer year, Model model) {
        LocalDate now = LocalDate.now();
        int m = month != null ? month : now.getMonthValue();
        int y = year != null ? year : now.getYear();
        model.addAttribute("report", reportService.monthlyFinancial(m, y));
        model.addAttribute("config", clinicConfigService.getConfig());
        addPeriod(model, m, y);
        return "reports/financial";
    }

    // ── Rapport d'activité médicale ─────────────────────────────────────────────
    @GetMapping("/activity")
    @PreAuthorize("hasAnyRole('MEDECIN','OWNER')")
    public String activity(@RequestParam(required = false) Integer month,
                           @RequestParam(required = false) Integer year, Model model) {
        LocalDate now = LocalDate.now();
        int m = month != null ? month : now.getMonthValue();
        int y = year != null ? year : now.getYear();
        model.addAttribute("report", reportService.activity(m, y));
        addPeriod(model, m, y);
        return "reports/activity";
    }

    // ── Statistiques épidémiologiques ───────────────────────────────────────────
    @GetMapping("/epidemiology")
    @PreAuthorize("hasAnyRole('MEDECIN','OWNER')")
    public String epidemiology(@RequestParam(required = false) Integer month,
                               @RequestParam(required = false) Integer year, Model model) {
        LocalDate now = LocalDate.now();
        int m = month != null ? month : now.getMonthValue();
        int y = year != null ? year : now.getYear();
        model.addAttribute("report", reportService.epidemiology(m, y));
        addPeriod(model, m, y);
        return "reports/epidemiology";
    }

    // ── Liste des impayés ───────────────────────────────────────────────────────
    @GetMapping("/outstanding")
    @PreAuthorize("hasAnyRole('OWNER','CAISSIER','SECRETAIRE')")
    public String outstanding(Model model) {
        model.addAttribute("report", reportService.outstanding());
        model.addAttribute("config", clinicConfigService.getConfig());
        return "reports/outstanding";
    }

    // ── Export Excel des impayés ────────────────────────────────────────────────
    @GetMapping("/outstanding/excel")
    @PreAuthorize("hasAnyRole('OWNER','CAISSIER','SECRETAIRE')")
    public ResponseEntity<byte[]> outstandingExcel() {
        return xlsxAttachment(reportExportService.outstandingExcel(reportService.outstanding()), "impayes.xlsx");
    }

    // ── Export PDF des impayés (le service existait, le bouton web manquait) ──────
    @GetMapping("/outstanding/pdf")
    @PreAuthorize("hasAnyRole('OWNER','CAISSIER','SECRETAIRE')")
    public ResponseEntity<byte[]> outstandingPdf() {
        return BillingWebController.pdfInline(
                reportExportService.outstandingPdf(reportService.outstanding()), "impayes.pdf");
    }

    // ── Exports Excel des rapports mensuels (services déjà présents, câblage web) ─
    @GetMapping("/financial/excel")
    @PreAuthorize("hasAnyRole('OWNER','CAISSIER')")
    public ResponseEntity<byte[]> financialExcel(@RequestParam(required = false) Integer month,
                                                 @RequestParam(required = false) Integer year) {
        int m = month != null ? month : LocalDate.now().getMonthValue();
        int y = year != null ? year : LocalDate.now().getYear();
        return xlsxAttachment(reportExportService.monthlyFinancialExcel(reportService.monthlyFinancial(m, y)),
                "bilan-financier-" + y + "-" + String.format("%02d", m) + ".xlsx");
    }

    @GetMapping("/activity/excel")
    @PreAuthorize("hasAnyRole('MEDECIN','OWNER')")
    public ResponseEntity<byte[]> activityExcel(@RequestParam(required = false) Integer month,
                                                @RequestParam(required = false) Integer year) {
        int m = month != null ? month : LocalDate.now().getMonthValue();
        int y = year != null ? year : LocalDate.now().getYear();
        return xlsxAttachment(reportExportService.activityExcel(reportService.activity(m, y)),
                "rapport-activite-" + y + "-" + String.format("%02d", m) + ".xlsx");
    }

    @GetMapping("/epidemiology/excel")
    @PreAuthorize("hasAnyRole('MEDECIN','OWNER')")
    public ResponseEntity<byte[]> epidemiologyExcel(@RequestParam(required = false) Integer month,
                                                    @RequestParam(required = false) Integer year) {
        int m = month != null ? month : LocalDate.now().getMonthValue();
        int y = year != null ? year : LocalDate.now().getYear();
        return xlsxAttachment(reportExportService.epidemiologyExcel(reportService.epidemiology(m, y)),
                "epidemiologie-" + y + "-" + String.format("%02d", m) + ".xlsx");
    }

    static ResponseEntity<byte[]> xlsxAttachment(byte[] xlsx, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(xlsx);
    }

    private void addPeriod(Model model, int month, int year) {
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        // Nom de mois localisé : suit la locale courante (cookie clinicLang) pour les vues web.
        // Les exports PDF (period(), documents officiels) restent en FR — voir docs/I18N-PLAN.md.
        model.addAttribute("monthName",
                Month.of(month).getDisplayName(TextStyle.FULL,
                        org.springframework.context.i18n.LocaleContextHolder.getLocale()));
    }

    // ── Exports PDF des rapports (délégués à ReportExportService) ────────────────

    @GetMapping("/financial/pdf")
    @PreAuthorize("hasAnyRole('OWNER','CAISSIER')")
    public ResponseEntity<byte[]> financialPdf(@RequestParam(required = false) Integer month,
                                               @RequestParam(required = false) Integer year) {
        int m = month != null ? month : LocalDate.now().getMonthValue();
        int y = year != null ? year : LocalDate.now().getYear();
        byte[] pdf = reportExportService.monthlyFinancialPdf(reportService.monthlyFinancial(m, y), m, y);
        return BillingWebController.pdfInline(pdf,
                "bilan-financier-" + y + "-" + String.format("%02d", m) + ".pdf");
    }

    @GetMapping("/activity/pdf")
    @PreAuthorize("hasAnyRole('MEDECIN','OWNER')")
    public ResponseEntity<byte[]> activityPdf(@RequestParam(required = false) Integer month,
                                              @RequestParam(required = false) Integer year) {
        int m = month != null ? month : LocalDate.now().getMonthValue();
        int y = year != null ? year : LocalDate.now().getYear();
        byte[] pdf = reportExportService.activityPdf(reportService.activity(m, y), m, y);
        return BillingWebController.pdfInline(pdf,
                "rapport-activite-" + y + "-" + String.format("%02d", m) + ".pdf");
    }

    @GetMapping("/epidemiology/pdf")
    @PreAuthorize("hasAnyRole('MEDECIN','OWNER')")
    public ResponseEntity<byte[]> epidemiologyPdf(@RequestParam(required = false) Integer month,
                                                  @RequestParam(required = false) Integer year) {
        int m = month != null ? month : LocalDate.now().getMonthValue();
        int y = year != null ? year : LocalDate.now().getYear();
        byte[] pdf = reportExportService.epidemiologyPdf(reportService.epidemiology(m, y), m, y);
        return BillingWebController.pdfInline(pdf,
                "epidemiologie-" + y + "-" + String.format("%02d", m) + ".pdf");
    }
}
