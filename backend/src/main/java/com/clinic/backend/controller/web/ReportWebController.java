package com.clinic.backend.controller.web;

import com.clinic.backend.clinicconfig.ClinicConfigService;
import com.clinic.backend.dto.InvoiceDto;
import com.clinic.backend.dto.MonthlyFinancialReportDto;
import com.clinic.backend.dto.OutstandingReportDto;
import com.clinic.backend.export.ExcelExportService;
import java.util.HashMap;
import java.util.Map;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Controller
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportWebController {

    private static final Locale FR = Locale.FRENCH;

    private final ReportService reportService;
    private final ClinicConfigService clinicConfigService;
    private final ExcelExportService excelExportService;
    private final com.clinic.backend.export.PdfExportService pdfExportService;

    // ── Hub Rapports ─────────────────────────────────────────────────────────────
    // Point d'entrée du lien « Rapports » de la sidebar. Chaque rôle est dirigé vers
    // le rapport qu'il a le droit de consulter (évite un 403 sur la page de direction
    // pour le caissier/secrétaire qui ont le module REPORTS mais pas le dashboard direction).
    @GetMapping({"", "/dashboard"})
    public String dashboard(Model model, org.springframework.security.core.Authentication auth) {
        // Cockpit financier (revenu, encaissé jour/mois) = pilotage business → OWNER seul.
        // L'ADMIN (technique) n'y a plus accès ; le MEDECIN non plus (P6, fuite financière colmatée).
        if (hasAnyRole(auth, "OWNER")) {
            model.addAttribute("dashboard", reportService.adminDashboard());
            model.addAttribute("config", clinicConfigService.getConfig());
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
        OutstandingReportDto report = reportService.outstanding();
        List<String> headers = List.of(
                "N° facture", "Patient", "Total", "Payé", "Reste à payer", "Statut", "Émise le");
        List<List<Object>> rows = new ArrayList<>();
        for (InvoiceDto inv : report.getInvoices()) {
            rows.add(java.util.Arrays.asList(
                    inv.getInvoiceNumber(),
                    inv.getPatientName(),
                    inv.getPatientAmount(),
                    inv.getPaidAmount(),
                    inv.getBalanceDue(),
                    inv.getStatus(),
                    inv.getCreatedAt() != null ? inv.getCreatedAt().toLocalDate().toString() : ""));
        }
        byte[] xlsx = excelExportService.toXlsx("Impayés", headers, rows);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"impayes.xlsx\"")
                .body(xlsx);
    }

    private void addPeriod(Model model, int month, int year) {
        model.addAttribute("month", month);
        model.addAttribute("year", year);
        model.addAttribute("monthName",
                Month.of(month).getDisplayName(TextStyle.FULL, FR));
    }

    // ── Exports PDF des rapports (template print générique) ──────────────────────

    @GetMapping("/financial/pdf")
    @PreAuthorize("hasAnyRole('OWNER','CAISSIER')")
    public ResponseEntity<byte[]> financialPdf(@RequestParam(required = false) Integer month,
                                               @RequestParam(required = false) Integer year) {
        int m = month != null ? month : LocalDate.now().getMonthValue();
        int y = year != null ? year : LocalDate.now().getYear();
        MonthlyFinancialReportDto r = reportService.monthlyFinancial(m, y);
        String cur = clinicConfigService.getConfig().getCurrency();

        List<Map<String, Object>> kpis = List.of(
                kpi("Facturé (part patient)", money(r.getTotalInvoiced(), cur)),
                kpi("Encaissé", money(r.getTotalCollected(), cur)),
                kpi("Reste à recouvrer", money(r.getTotalOutstanding(), cur)),
                kpi("Nombre de factures", r.getInvoiceCount()));

        List<Map<String, Object>> rows = new ArrayList<>();
        r.getCollectedByMethod().forEach((method, amount) ->
                rows.add(row(method, money(amount, cur))));
        List<Map<String, Object>> sections = List.of(
                section("Encaissements par mode de paiement", "Mode", "Montant", rows));

        return reportPdf("Bilan financier", period(m, y), kpis, sections,
                "bilan-financier-" + y + "-" + String.format("%02d", m) + ".pdf");
    }

    @GetMapping("/activity/pdf")
    @PreAuthorize("hasAnyRole('MEDECIN','OWNER')")
    public ResponseEntity<byte[]> activityPdf(@RequestParam(required = false) Integer month,
                                              @RequestParam(required = false) Integer year) {
        int m = month != null ? month : LocalDate.now().getMonthValue();
        int y = year != null ? year : LocalDate.now().getYear();
        var r = reportService.activity(m, y);

        List<Map<String, Object>> kpis = List.of(
                kpi("Consultations", r.getConsultations()),
                kpi("Rendez-vous", r.getAppointments()),
                kpi("Nouveaux patients", r.getNewPatients()),
                kpi("Demandes de laboratoire", r.getLabRequests()),
                kpi("Admissions", r.getAdmissions()));

        List<Map<String, Object>> sections = List.of(
                section("Consultations par département", "Département", "Nombre",
                        rowsOf(r.getConsultationsByDepartment())));

        return reportPdf("Rapport d'activité", period(m, y), kpis, sections,
                "rapport-activite-" + y + "-" + String.format("%02d", m) + ".pdf");
    }

    @GetMapping("/epidemiology/pdf")
    @PreAuthorize("hasAnyRole('MEDECIN','OWNER')")
    public ResponseEntity<byte[]> epidemiologyPdf(@RequestParam(required = false) Integer month,
                                                  @RequestParam(required = false) Integer year) {
        int m = month != null ? month : LocalDate.now().getMonthValue();
        int y = year != null ? year : LocalDate.now().getYear();
        var r = reportService.epidemiology(m, y);

        List<Map<String, Object>> kpis = List.of(
                kpi("Consultations sur la période", r.getTotalConsultations()));

        List<Map<String, Object>> sections = List.of(
                section("Principales pathologies", "Pathologie", "Cas", rowsOf(r.getTopPathologies())),
                section("Répartition par tranche d'âge", "Tranche", "Patients", rowsOf(r.getByAgeGroup())),
                section("Répartition par sexe", "Sexe", "Patients", rowsOf(r.getBySex())),
                section("Répartition par département", "Département", "Consultations", rowsOf(r.getByDepartment())));

        return reportPdf("Statistiques épidémiologiques", period(m, y), kpis, sections,
                "epidemiologie-" + y + "-" + String.format("%02d", m) + ".pdf");
    }

    // ── Helpers d'export rapport ────────────────────────────────────────────────

    private ResponseEntity<byte[]> reportPdf(String title, String period,
                                             List<Map<String, Object>> kpis,
                                             List<Map<String, Object>> sections, String filename) {
        Map<String, Object> model = new HashMap<>();
        model.put("config", clinicConfigService.getConfig());
        model.put("title", title);
        model.put("period", period);
        model.put("kpis", kpis);
        model.put("sections", sections);
        model.put("generatedAt", LocalDate.now().toString());
        byte[] pdf = pdfExportService.renderTemplate("reports/pdf-report", model);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(pdf);
    }

    private String period(int month, int year) {
        return Month.of(month).getDisplayName(TextStyle.FULL, FR) + " " + year;
    }

    private static String money(java.math.BigDecimal v, String currency) {
        java.math.BigDecimal amount = v != null ? v : java.math.BigDecimal.ZERO;
        return amount.stripTrailingZeros().toPlainString() + " " + (currency != null ? currency : "");
    }

    private static Map<String, Object> kpi(String label, Object value) {
        return Map.of("label", label, "value", String.valueOf(value));
    }

    private static Map<String, Object> row(String label, String value) {
        return Map.of("label", label != null ? label : "—", "value", value != null ? value : "");
    }

    private static Map<String, Object> section(String title, String col1, String col2,
                                               List<Map<String, Object>> rows) {
        Map<String, Object> m = new HashMap<>();
        m.put("title", title);
        m.put("col1", col1);
        m.put("col2", col2);
        m.put("rows", rows);
        return m;
    }

    private static List<Map<String, Object>> rowsOf(List<com.clinic.backend.dto.LabelValueDto> items) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (items != null) {
            for (com.clinic.backend.dto.LabelValueDto lv : items) {
                rows.add(row(lv.getLabel(), String.valueOf(lv.getCount())));
            }
        }
        return rows;
    }
}
