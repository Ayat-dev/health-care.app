package com.clinic.backend.controller.web;

import com.clinic.backend.billing.PaymentWebhookEvent;
import com.clinic.backend.billing.PaymentWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;

/**
 * Journal des webhooks de paiement Mobile Money (Z4a) — lecture seule.
 * <p>
 * <b>Réservé au SUPER_ADMIN</b> : la table {@code payment_webhook_events} est globale
 * (le webhook arrive sans contexte de tenant, cf. {@code PaymentWebhookService}), donc
 * elle n'est pas cloisonnée par clinique — seul le rôle transverse peut la consulter sans
 * risque de fuite inter-cliniques. Filtres optionnels : fournisseur, statut, plage de dates.
 */
@Controller
@RequestMapping("/admin/payment-webhooks")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminPaymentWebhookWebController {

    private static final int MAX_ROWS = 200;

    private final PaymentWebhookEventRepository eventRepository;

    @GetMapping
    public String list(@RequestParam(required = false) String provider,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       Model model) {
        LocalDateTime fromTs = (from != null) ? from.atStartOfDay() : null;
        LocalDateTime toTs   = (to != null) ? to.atTime(LocalTime.MAX) : null;

        model.addAttribute("events", eventRepository.search(
                blankToNull(provider), blankToNull(status), fromTs, toTs, PageRequest.of(0, MAX_ROWS)));
        model.addAttribute("providers", eventRepository.distinctProviders());
        model.addAttribute("statuses",
                Arrays.stream(PaymentWebhookEvent.Status.values()).map(Enum::name).toList());
        model.addAttribute("provider", provider);
        model.addAttribute("status", status);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("maxRows", MAX_ROWS);
        return "admin/payment-webhooks/list";
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
