package com.clinic.backend.controller.api;

import com.clinic.backend.billing.BillingService;
import com.clinic.backend.catalog.ActCatalogService;
import com.clinic.backend.dto.ActCatalogDto;
import com.clinic.backend.dto.DailyCashReportDto;
import com.clinic.backend.dto.InsuranceProviderDto;
import com.clinic.backend.dto.InvoiceDto;
import com.clinic.backend.dto.PaymentDto;
import com.clinic.backend.insurance.InsuranceProviderService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class BillingApiController {

    private final BillingService billingService;
    private final ActCatalogService actCatalogService;
    private final InsuranceProviderService insuranceProviderService;

    // ── Factures ─────────────────────────────────────────────────────────────────
    @GetMapping("/invoices")
    public List<InvoiceDto> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) String status) {
        return billingService.searchDto(from, to, patientId, status);
    }

    @GetMapping("/invoices/{id}")
    public InvoiceDto get(@PathVariable Long id) {
        return billingService.getDtoById(id);
    }

    @PostMapping("/invoices")
    @PreAuthorize("hasAnyRole('CAISSIER','SECRETAIRE','ADMIN')")
    public ResponseEntity<InvoiceDto> create(@RequestBody InvoiceDto dto) {
        Long id = billingService.create(dto).getId();
        return ResponseEntity.ok(billingService.getDtoById(id));
    }

    @PutMapping("/invoices/{id}")
    @PreAuthorize("hasAnyRole('CAISSIER','SECRETAIRE','ADMIN')")
    public InvoiceDto update(@PathVariable Long id, @RequestBody InvoiceDto dto) {
        billingService.update(id, dto);
        return billingService.getDtoById(id);
    }

    @PostMapping("/invoices/{id}/pay")
    @PreAuthorize("hasAnyRole('CAISSIER','ADMIN')")
    public InvoiceDto pay(@PathVariable Long id, @RequestBody PaymentDto dto) {
        billingService.recordPayment(id, dto);
        return billingService.getDtoById(id);
    }

    @PatchMapping("/invoices/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public InvoiceDto cancel(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        billingService.cancel(id, reason);
        return billingService.getDtoById(id);
    }

    // ── Catalogues d'appui ─────────────────────────────────────────────────────────
    @GetMapping("/acts")
    public List<ActCatalogDto> acts() {
        return actCatalogService.listActiveAsDto();
    }

    @GetMapping("/insurance")
    public List<InsuranceProviderDto> insurance() {
        return insuranceProviderService.listActive().stream()
                .map(insuranceProviderService::toDto).toList();
    }

    // ── Rapports ─────────────────────────────────────────────────────────────────────
    @GetMapping("/reports/daily")
    public DailyCashReportDto daily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return billingService.dailyReport(date);
    }
}
