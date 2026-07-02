package com.clinic.backend.controller.web;

import com.clinic.backend.billing.BillingService;
import com.clinic.backend.billing.Invoice;
import com.clinic.backend.catalog.ActCatalogService;
import com.clinic.backend.clinicconfig.ClinicConfigService;
import com.clinic.backend.dto.InvoiceDto;
import com.clinic.backend.dto.PaymentDto;
import com.clinic.backend.export.PdfExportService;
import com.clinic.backend.i18n.WebI18n;
import com.clinic.backend.insurance.InsuranceProviderService;
import com.clinic.backend.patient.Patient;
import com.clinic.backend.patient.PatientService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.Period;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/billing")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER','CAISSIER','SECRETAIRE')") // finances : ADMIN→OWNER (P6)
public class BillingWebController {

    private final BillingService billingService;
    private final PatientService patientService;
    private final ActCatalogService actCatalogService;
    private final InsuranceProviderService insuranceProviderService;
    private final ClinicConfigService clinicConfigService;
    private final PdfExportService pdfExportService;
    private final com.clinic.backend.export.FicheExportService ficheExportService;
    private final WebI18n i18n;

    // ── Tableau de bord financier ────────────────────────────────────────────────
    @GetMapping({"", "/dashboard"})
    public String dashboard(Model model) {
        model.addAttribute("dashboard", billingService.dashboard());
        model.addAttribute("report", billingService.dailyReport(LocalDate.now()));
        model.addAttribute("config", clinicConfigService.getConfig());
        return "billing/dashboard";
    }

    // ── File d'attente caisse (la « pile » par patient à encaisser) ──────────────────
    @GetMapping("/queue")
    public String queue(Model model) {
        model.addAttribute("invoices", billingService.cashierQueue());
        return "billing/queue";
    }

    // ── Rapprochement manuel des paiements QR AmanaTa/MyNITA (Z4b) ────────────────────
    @GetMapping("/reconciliation")
    @PreAuthorize("hasAnyRole('OWNER','CAISSIER')")
    public String reconciliation(@RequestParam(required = false)
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
                                 @RequestParam(defaultValue = "false") boolean pendingOnly,
                                 Model model) {
        LocalDate d = day != null ? day : LocalDate.now();
        model.addAttribute("report", billingService.reconciliationReport(d, pendingOnly));
        model.addAttribute("day", d);
        model.addAttribute("pendingOnly", pendingOnly);
        model.addAttribute("config", clinicConfigService.getConfig());
        return "billing/reconciliation";
    }

    @PostMapping("/reconciliation/{paymentId}/toggle")
    @PreAuthorize("hasAnyRole('OWNER','CAISSIER')")
    public String toggleReconciled(@PathVariable Long paymentId,
                                   @RequestParam(required = false)
                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
                                   @RequestParam(defaultValue = "false") boolean pendingOnly,
                                   RedirectAttributes ra) {
        try {
            billingService.toggleReconciled(paymentId);
            ra.addFlashAttribute("success", "billing.reconciliation.flash_done");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        LocalDate d = day != null ? day : LocalDate.now();
        return "redirect:/billing/reconciliation?day=" + d + (pendingOnly ? "&pendingOnly=true" : "");
    }

    // ── Liste des factures ───────────────────────────────────────────────────────
    @GetMapping("/invoices")
    public String list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(required = false) String status,
                       Model model) {
        model.addAttribute("invoices", billingService.searchDto(from, to, null, status));
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("status", status);
        return "billing/invoices/list";
    }

    // ── Export Excel du journal des factures (compta) ────────────────────────────────
    @GetMapping("/invoices/export")
    public ResponseEntity<byte[]> exportInvoices(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String status) {
        byte[] xlsx = ficheExportService.invoicesXlsx(billingService.searchDto(from, to, null, status));
        return ReportWebController.xlsxAttachment(xlsx, "factures.xlsx");
    }

    // ── Détail ─────────────────────────────────────────────────────────────────────
    @GetMapping("/invoices/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("invoice", billingService.getDtoById(id));
        return "billing/invoices/detail";
    }

    // ── Création ────────────────────────────────────────────────────────────────────
    @GetMapping("/invoices/new")
    public String newForm(@RequestParam(required = false) Long consultationId,
                          @RequestParam(required = false) Long hospitalizationId,
                          @RequestParam(required = false) Long patientId,
                          Model model) {
        InvoiceDto invoice = hospitalizationId != null
                ? billingService.prefillFromHospitalization(hospitalizationId)
                : billingService.prefillFromConsultation(consultationId, patientId);
        model.addAttribute("invoice", invoice);
        populateFormOptions(model);
        return "billing/invoices/form";
    }

    @PostMapping("/invoices/new")
    public String create(@ModelAttribute InvoiceDto dto, RedirectAttributes ra, Model model) {
        try {
            Invoice created = billingService.create(dto);
            ra.addFlashAttribute("success", i18n.t("billing.flash.created", created.getInvoiceNumber()));
            return "redirect:/billing/invoices/" + created.getId();
        } catch (RuntimeException e) {
            model.addAttribute("invoice", dto);
            model.addAttribute("error", e.getMessage());
            populateFormOptions(model);
            return "billing/invoices/form";
        }
    }

    // ── Modification (EN_ATTENTE) ────────────────────────────────────────────────────
    @GetMapping("/invoices/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("invoice", billingService.getDtoById(id));
        populateFormOptions(model);
        return "billing/invoices/form";
    }

    @PostMapping("/invoices/{id}/edit")
    public String update(@PathVariable Long id, @ModelAttribute InvoiceDto dto,
                         RedirectAttributes ra, Model model) {
        try {
            billingService.update(id, dto);
            ra.addFlashAttribute("success", i18n.t("billing.flash.updated"));
            return "redirect:/billing/invoices/" + id;
        } catch (RuntimeException e) {
            model.addAttribute("invoice", billingService.getDtoById(id));
            model.addAttribute("error", e.getMessage());
            populateFormOptions(model);
            return "billing/invoices/form";
        }
    }

    // ── Encaissement ──────────────────────────────────────────────────────────────────
    @GetMapping("/invoices/{id}/pay")
    @PreAuthorize("hasRole('CAISSIER')") // encaissement = caisse (P6) — SECRETAIRE/OWNER exclus, aligné sur l'API
    public String payForm(@PathVariable Long id, Model model) {
        model.addAttribute("invoice", billingService.getDtoById(id));
        model.addAttribute("payment", new PaymentDto());
        model.addAttribute("config", clinicConfigService.getConfig());
        return "billing/invoices/pay";
    }

    @PostMapping("/invoices/{id}/pay")
    @PreAuthorize("hasRole('CAISSIER')") // encaissement = caisse (P6) — SECRETAIRE/OWNER exclus, aligné sur l'API
    public String pay(@PathVariable Long id, @ModelAttribute PaymentDto dto,
                      RedirectAttributes ra, Model model) {
        try {
            billingService.recordPayment(id, dto);
            ra.addFlashAttribute("success", i18n.t("billing.flash.payment_recorded"));
            return "redirect:/billing/invoices/" + id;
        } catch (RuntimeException e) {
            model.addAttribute("invoice", billingService.getDtoById(id));
            model.addAttribute("payment", dto);
            model.addAttribute("config", clinicConfigService.getConfig());
            model.addAttribute("error", e.getMessage());
            return "billing/invoices/pay";
        }
    }

    // ── Annulation ──────────────────────────────────────────────────────────────────────
    @PostMapping("/invoices/{id}/cancel")
    @PreAuthorize("hasRole('OWNER')") // annulation = décision business → OWNER (P6), aligné sur l'API
    public String cancel(@PathVariable Long id, @RequestParam(required = false) String reason,
                         RedirectAttributes ra) {
        try {
            billingService.cancel(id, reason);
            ra.addFlashAttribute("success", i18n.t("billing.flash.cancelled"));
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/billing/invoices/" + id;
    }

    // ── Reçu imprimable (HTML) ─────────────────────────────────────────────────────────────
    @GetMapping("/invoices/{id}/receipt")
    public String receipt(@PathVariable Long id, Model model) {
        Map<String, Object> data = receiptModel(id);
        model.addAllAttributes(data);
        model.addAttribute("pdf", false); // affiche la toolbar dans le navigateur
        return "billing/invoices/receipt";
    }

    // ── Reçu téléchargeable (PDF) ──────────────────────────────────────────────────────────
    @GetMapping("/invoices/{id}/receipt/pdf")
    public ResponseEntity<byte[]> receiptPdf(@PathVariable Long id) {
        InvoiceDto invoice = billingService.getDtoById(id);
        byte[] pdf = pdfExportService.renderTemplate("billing/invoices/receipt", receiptModel(id));
        return pdfInline(pdf, "recu-" + invoice.getInvoiceNumber() + ".pdf");
    }

    /** Modèle partagé entre la vue HTML et l'export PDF du reçu. */
    private Map<String, Object> receiptModel(Long id) {
        InvoiceDto invoice = billingService.getDtoById(id);
        Map<String, Object> model = new HashMap<>();
        model.put("invoice", invoice);
        model.put("config", clinicConfigService.getConfig());

        Integer age = null;
        if (invoice.getPatientId() != null) {
            Patient patient = patientService.getById(invoice.getPatientId());
            if (patient.getBirthDate() != null) {
                age = Period.between(patient.getBirthDate(), LocalDate.now()).getYears();
            }
        }
        model.put("patientAge", age);
        return model;
    }

    public static ResponseEntity<byte[]> pdfInline(byte[] pdf, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(pdf);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────────
    private void populateFormOptions(Model model) {
        model.addAttribute("patients", patientService.search("", 0, 500).getContent());
        model.addAttribute("acts", actCatalogService.listActiveAsDto());
        model.addAttribute("insurers", insuranceProviderService.listActive());
    }
}
