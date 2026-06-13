package com.clinic.backend.controller.api;

import com.clinic.backend.dto.*;
import com.clinic.backend.reports.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Read-only reporting API (module 14). Pure aggregation over the other modules — no writes.
 * Binary exports ({@code format=pdf|excel}) are deferred until a PDF/Excel lib is on the
 * classpath; the web views are print-optimized in the meantime (same approach as the
 * lab/radiology bulletins and the billing receipt).
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportApiController {

    private final ReportService reportService;

    // ── Tableaux de bord ────────────────────────────────────────────────────────
    @GetMapping("/dashboard/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminDashboardDto adminDashboard() {
        return reportService.adminDashboard();
    }

    @GetMapping("/dashboard/doctor")
    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    public DoctorDashboardDto doctorDashboard() {
        return reportService.doctorDashboard();
    }

    @GetMapping("/dashboard/pharmacy")
    @PreAuthorize("hasAnyRole('PHARMACIEN','ADMIN')")
    public PharmacyDashboardDto pharmacyDashboard() {
        return reportService.pharmacyDashboard();
    }

    // ── Rapports ────────────────────────────────────────────────────────────────
    @GetMapping("/daily-cash")
    @PreAuthorize("hasAnyRole('CAISSIER','ADMIN')")
    public DailyCashReportDto dailyCash(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return reportService.dailyCash(date);
    }

    @GetMapping("/monthly-financial")
    @PreAuthorize("hasAnyRole('CAISSIER','ADMIN')")
    public MonthlyFinancialReportDto monthlyFinancial(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        LocalDate now = LocalDate.now();
        return reportService.monthlyFinancial(
                month != null ? month : now.getMonthValue(),
                year != null ? year : now.getYear());
    }

    @GetMapping("/activity")
    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    public ActivityReportDto activity(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        LocalDate now = LocalDate.now();
        return reportService.activity(
                month != null ? month : now.getMonthValue(),
                year != null ? year : now.getYear());
    }

    @GetMapping("/epidemiology")
    @PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
    public EpidemiologyReportDto epidemiology(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        LocalDate now = LocalDate.now();
        return reportService.epidemiology(
                month != null ? month : now.getMonthValue(),
                year != null ? year : now.getYear());
    }

    @GetMapping("/outstanding")
    @PreAuthorize("hasAnyRole('CAISSIER','SECRETAIRE','ADMIN')")
    public OutstandingReportDto outstanding() {
        return reportService.outstanding();
    }

    @GetMapping("/stock")
    @PreAuthorize("hasAnyRole('PHARMACIEN','ADMIN')")
    public List<StockItemDto> stock() {
        return reportService.stock();
    }
}
