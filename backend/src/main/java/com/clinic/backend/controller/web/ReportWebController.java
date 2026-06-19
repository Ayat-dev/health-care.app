package com.clinic.backend.controller.web;

import com.clinic.backend.clinicconfig.ClinicConfigService;
import com.clinic.backend.dto.InvoiceDto;
import com.clinic.backend.dto.OutstandingReportDto;
import com.clinic.backend.export.ExcelExportService;
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

    // ── Tableau de bord (KPIs direction) ───────────────────────────────────────
    @GetMapping({"", "/dashboard"})
    @PreAuthorize("hasAnyRole('ADMIN','MEDECIN')")
    public String dashboard(Model model) {
        model.addAttribute("dashboard", reportService.adminDashboard());
        model.addAttribute("config", clinicConfigService.getConfig());
        return "reports/dashboard";
    }

    // ── Bilan financier mensuel ─────────────────────────────────────────────────
    @GetMapping("/financial")
    @PreAuthorize("hasAnyRole('ADMIN','CAISSIER')")
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
    @PreAuthorize("hasAnyRole('ADMIN','MEDECIN')")
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
    @PreAuthorize("hasAnyRole('ADMIN','MEDECIN')")
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
    @PreAuthorize("hasAnyRole('ADMIN','CAISSIER','SECRETAIRE')")
    public String outstanding(Model model) {
        model.addAttribute("report", reportService.outstanding());
        model.addAttribute("config", clinicConfigService.getConfig());
        return "reports/outstanding";
    }

    // ── Export Excel des impayés ────────────────────────────────────────────────
    @GetMapping("/outstanding/excel")
    @PreAuthorize("hasAnyRole('ADMIN','CAISSIER','SECRETAIRE')")
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
}
