package com.clinic.backend.controller.web;

import com.clinic.backend.billing.BillingService;
import com.clinic.backend.billing.Invoice;
import com.clinic.backend.catalog.ActCatalogService;
import com.clinic.backend.clinicconfig.ClinicConfigService;
import com.clinic.backend.dto.InvoiceDto;
import com.clinic.backend.dto.PaymentDto;
import com.clinic.backend.insurance.InsuranceProviderService;
import com.clinic.backend.patient.Patient;
import com.clinic.backend.patient.PatientService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.Period;

@Controller
@RequestMapping("/billing")
@RequiredArgsConstructor
public class BillingWebController {

    private final BillingService billingService;
    private final PatientService patientService;
    private final ActCatalogService actCatalogService;
    private final InsuranceProviderService insuranceProviderService;
    private final ClinicConfigService clinicConfigService;

    // ── Tableau de bord financier ────────────────────────────────────────────────
    @GetMapping({"", "/dashboard"})
    public String dashboard(Model model) {
        model.addAttribute("dashboard", billingService.dashboard());
        model.addAttribute("report", billingService.dailyReport(LocalDate.now()));
        model.addAttribute("config", clinicConfigService.getConfig());
        return "billing/dashboard";
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
            ra.addFlashAttribute("success", "Facture " + created.getInvoiceNumber() + " créée.");
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
            ra.addFlashAttribute("success", "Facture mise à jour.");
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
    public String payForm(@PathVariable Long id, Model model) {
        model.addAttribute("invoice", billingService.getDtoById(id));
        model.addAttribute("payment", new PaymentDto());
        model.addAttribute("config", clinicConfigService.getConfig());
        return "billing/invoices/pay";
    }

    @PostMapping("/invoices/{id}/pay")
    public String pay(@PathVariable Long id, @ModelAttribute PaymentDto dto,
                      RedirectAttributes ra, Model model) {
        try {
            billingService.recordPayment(id, dto);
            ra.addFlashAttribute("success", "Paiement enregistré.");
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
    public String cancel(@PathVariable Long id, @RequestParam(required = false) String reason,
                         RedirectAttributes ra) {
        try {
            billingService.cancel(id, reason);
            ra.addFlashAttribute("success", "Facture annulée.");
        } catch (RuntimeException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/billing/invoices/" + id;
    }

    // ── Reçu imprimable ───────────────────────────────────────────────────────────────────
    @GetMapping("/invoices/{id}/receipt")
    public String receipt(@PathVariable Long id, Model model) {
        InvoiceDto invoice = billingService.getDtoById(id);
        model.addAttribute("invoice", invoice);
        model.addAttribute("config", clinicConfigService.getConfig());

        Integer age = null;
        if (invoice.getPatientId() != null) {
            Patient patient = patientService.getById(invoice.getPatientId());
            if (patient.getBirthDate() != null) {
                age = Period.between(patient.getBirthDate(), LocalDate.now()).getYears();
            }
        }
        model.addAttribute("patientAge", age);
        return "billing/invoices/receipt";
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────────
    private void populateFormOptions(Model model) {
        model.addAttribute("patients", patientService.search("", 0, 500).getContent());
        model.addAttribute("acts", actCatalogService.listActiveAsDto());
        model.addAttribute("insurers", insuranceProviderService.listActive());
    }
}
