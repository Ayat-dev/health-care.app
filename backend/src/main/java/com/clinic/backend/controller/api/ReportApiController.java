package com.clinic.backend.controller.api;

import com.clinic.backend.dto.*;
import com.clinic.backend.export.ReportExportService;
import com.clinic.backend.reports.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Read-only reporting API (module 14). Pure aggregation over the other modules — no writes.
 * <p>
 * Les rapports tabulaires acceptent {@code ?format=pdf|excel} (D4a) pour les clients
 * API / desktop : la sérialisation JSON reste le défaut, {@code pdf}/{@code excel}
 * renvoient un document binaire en pièce jointe (réutilise {@link ReportExportService}).
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportApiController {

    private static final String XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ReportService reportService;
    private final ReportExportService reportExportService;

    // ── Tableaux de bord (JSON uniquement — vues composites) ─────────────────────
    @GetMapping("/dashboard/admin")
    @PreAuthorize("hasRole('OWNER')")
    public AdminDashboardDto adminDashboard() {
        return reportService.adminDashboard();
    }

    @GetMapping("/dashboard/doctor")
    @PreAuthorize("hasRole('MEDECIN')")
    public DoctorDashboardDto doctorDashboard() {
        return reportService.doctorDashboard();
    }

    @GetMapping("/dashboard/pharmacy")
    @PreAuthorize("hasAnyRole('PHARMACIEN','OWNER')")
    public PharmacyDashboardDto pharmacyDashboard() {
        return reportService.pharmacyDashboard();
    }

    // ── Rapports (JSON / pdf / excel) ────────────────────────────────────────────
    @GetMapping("/daily-cash")
    @PreAuthorize("hasAnyRole('CAISSIER','OWNER')")
    public ResponseEntity<?> dailyCash(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String format) {
        DailyCashReportDto dto = reportService.dailyCash(date);
        String name = "caisse-" + (dto.getDay() != null ? dto.getDay() : LocalDate.now());
        if (isPdf(format)) return pdf(reportExportService.dailyCashPdf(dto), name + ".pdf");
        if (isExcel(format)) return xlsx(reportExportService.dailyCashExcel(dto), name + ".xlsx");
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/monthly-financial")
    @PreAuthorize("hasAnyRole('CAISSIER','OWNER')")
    public ResponseEntity<?> monthlyFinancial(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String format) {
        LocalDate now = LocalDate.now();
        int m = month != null ? month : now.getMonthValue();
        int y = year != null ? year : now.getYear();
        MonthlyFinancialReportDto dto = reportService.monthlyFinancial(m, y);
        String name = "bilan-financier-" + y + "-" + fmt(m);
        if (isPdf(format)) return pdf(reportExportService.monthlyFinancialPdf(dto, m, y), name + ".pdf");
        if (isExcel(format)) return xlsx(reportExportService.monthlyFinancialExcel(dto), name + ".xlsx");
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/activity")
    @PreAuthorize("hasAnyRole('MEDECIN','OWNER')")
    public ResponseEntity<?> activity(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String format) {
        LocalDate now = LocalDate.now();
        int m = month != null ? month : now.getMonthValue();
        int y = year != null ? year : now.getYear();
        ActivityReportDto dto = reportService.activity(m, y);
        String name = "rapport-activite-" + y + "-" + fmt(m);
        if (isPdf(format)) return pdf(reportExportService.activityPdf(dto, m, y), name + ".pdf");
        if (isExcel(format)) return xlsx(reportExportService.activityExcel(dto), name + ".xlsx");
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/epidemiology")
    @PreAuthorize("hasAnyRole('MEDECIN','OWNER')")
    public ResponseEntity<?> epidemiology(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String format) {
        LocalDate now = LocalDate.now();
        int m = month != null ? month : now.getMonthValue();
        int y = year != null ? year : now.getYear();
        EpidemiologyReportDto dto = reportService.epidemiology(m, y);
        String name = "epidemiologie-" + y + "-" + fmt(m);
        if (isPdf(format)) return pdf(reportExportService.epidemiologyPdf(dto, m, y), name + ".pdf");
        if (isExcel(format)) return xlsx(reportExportService.epidemiologyExcel(dto), name + ".xlsx");
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/outstanding")
    @PreAuthorize("hasAnyRole('CAISSIER','SECRETAIRE','OWNER')")
    public ResponseEntity<?> outstanding(@RequestParam(required = false) String format) {
        OutstandingReportDto dto = reportService.outstanding();
        if (isPdf(format)) return pdf(reportExportService.outstandingPdf(dto), "impayes.pdf");
        if (isExcel(format)) return xlsx(reportExportService.outstandingExcel(dto), "impayes.xlsx");
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/stock")
    @PreAuthorize("hasAnyRole('PHARMACIEN','OWNER')")
    public ResponseEntity<?> stock(@RequestParam(required = false) String format) {
        List<StockItemDto> dto = reportService.stock();
        if (isPdf(format)) return pdf(reportExportService.stockPdf(dto), "stock.pdf");
        if (isExcel(format)) return xlsx(reportExportService.stockExcel(dto), "stock.xlsx");
        return ResponseEntity.ok(dto);
    }

    // ── Helpers d'export ─────────────────────────────────────────────────────────
    private static boolean isPdf(String f) {
        return "pdf".equalsIgnoreCase(f);
    }

    private static boolean isExcel(String f) {
        return "excel".equalsIgnoreCase(f) || "xlsx".equalsIgnoreCase(f);
    }

    private static String fmt(int month) {
        return String.format("%02d", month);
    }

    private static ResponseEntity<byte[]> pdf(byte[] body, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }

    private static ResponseEntity<byte[]> xlsx(byte[] body, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(XLSX_MIME))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }
}
