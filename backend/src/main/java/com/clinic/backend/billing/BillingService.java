package com.clinic.backend.billing;

import com.clinic.backend.catalog.ActCatalog;
import com.clinic.backend.catalog.ActCatalogRepository;
import com.clinic.backend.clinicconfig.ClinicConfig;
import com.clinic.backend.clinicconfig.ClinicConfigService;
import com.clinic.backend.consultation.Consultation;
import com.clinic.backend.consultation.ConsultationRepository;
import com.clinic.backend.dto.BillingDashboardDto;
import com.clinic.backend.dto.DailyCashReportDto;
import com.clinic.backend.dto.InvoiceDto;
import com.clinic.backend.dto.InvoiceItemDto;
import com.clinic.backend.dto.PaymentDto;
import com.clinic.backend.hospitalization.Hospitalization;
import com.clinic.backend.hospitalization.HospitalizationRepository;
import com.clinic.backend.insurance.InsuranceProvider;
import com.clinic.backend.insurance.InsuranceProviderRepository;
import com.clinic.backend.model.User;
import com.clinic.backend.patient.Patient;
import com.clinic.backend.patient.PatientRepository;
import com.clinic.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class BillingService {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final PatientRepository patientRepository;
    private final ConsultationRepository consultationRepository;
    private final HospitalizationRepository hospitalizationRepository;
    private final InsuranceProviderRepository insuranceProviderRepository;
    private final ActCatalogRepository actCatalogRepository;
    private final UserRepository userRepository;
    private final ClinicConfigService clinicConfigService;

    // ── Listes / recherche ────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<InvoiceDto> searchDto(LocalDate from, LocalDate to, Long patientId, String status) {
        LocalDateTime fromTs = from != null ? from.atStartOfDay() : null;
        LocalDateTime toTs   = to   != null ? to.plusDays(1).atStartOfDay() : null;
        return invoiceRepository.search(fromTs, toTs, patientId, status).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<InvoiceDto> findForPatient(Long patientId) {
        return invoiceRepository.findByPatient(patientId).stream().map(this::toDto).toList();
    }

    // ── Détail ────────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public Invoice getById(Long id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Facture introuvable : " + id));
    }

    @Transactional(readOnly = true)
    public InvoiceDto getDtoById(Long id) {
        Invoice inv = invoiceRepository.findWithRefsById(id)
                .orElseThrow(() -> new IllegalArgumentException("Facture introuvable : " + id));
        return toDto(inv);
    }

    // ── Pré-remplissage depuis les modules amont ────────────────────────────────
    /** Prefill a new invoice from a consultation (patient + a consultation line). */
    @Transactional(readOnly = true)
    public InvoiceDto prefillFromConsultation(Long consultationId, Long patientId) {
        InvoiceDto dto = new InvoiceDto();
        Patient patient = null;
        if (consultationId != null) {
            Consultation c = consultationRepository.findWithRefsById(consultationId)
                    .orElseThrow(() -> new IllegalArgumentException("Consultation introuvable : " + consultationId));
            dto.setConsultationId(c.getId());
            patient = c.getPatient();
            dto.getItems().add(consultationLine());
        }
        if (patient == null && patientId != null) {
            patient = patientRepository.findByIdAndDeletedAtIsNull(patientId).orElse(null);
        }
        if (patient != null) {
            dto.setPatientId(patient.getId());
            dto.setPatientName(patient.getFullName());
            dto.setPatientRecordNumber(patient.getRecordNumber());
        }
        if (dto.getItems().isEmpty()) dto.getItems().add(new InvoiceItemDto());
        return dto;
    }

    /** Prefill a new invoice from a hospitalization (patient + a stay line nights×daily_rate). */
    @Transactional(readOnly = true)
    public InvoiceDto prefillFromHospitalization(Long hospitalizationId) {
        InvoiceDto dto = new InvoiceDto();
        Hospitalization h = hospitalizationRepository.findWithRefsById(hospitalizationId)
                .orElseThrow(() -> new IllegalArgumentException("Séjour introuvable : " + hospitalizationId));
        dto.setHospitalizationId(h.getId());
        Patient patient = h.getPatient();
        if (patient != null) {
            dto.setPatientId(patient.getId());
            dto.setPatientName(patient.getFullName());
            dto.setPatientRecordNumber(patient.getRecordNumber());
        }
        long nights = stayNights(h);
        BigDecimal rate = h.getRoom() != null ? h.getRoom().getDailyRate() : BigDecimal.ZERO;
        InvoiceItemDto line = new InvoiceItemDto();
        line.setDescription("Séjour hospitalier — chambre "
                + (h.getRoom() != null ? h.getRoom().getRoomNumber() : "?") + " (" + nights + " nuit(s))");
        line.setQuantity((int) nights);
        line.setUnitPrice(rate);
        dto.getItems().add(line);
        return dto;
    }

    private InvoiceItemDto consultationLine() {
        InvoiceItemDto line = new InvoiceItemDto();
        ActCatalog act = actCatalogRepository.findByCodeIgnoreCase("CONS_GEN").orElse(null);
        if (act != null) {
            line.setActId(act.getId());
            line.setActCode(act.getCode());
            line.setDescription(act.getName());
            line.setUnitPrice(act.getPrice());
        } else {
            line.setDescription("Consultation");
            line.setUnitPrice(BigDecimal.ZERO);
        }
        line.setQuantity(1);
        return line;
    }

    private long stayNights(Hospitalization h) {
        LocalDateTime end = h.getDischargeDate() != null ? h.getDischargeDate() : LocalDateTime.now();
        long nights = Duration.between(h.getAdmissionDate(), end).toDays();
        return Math.max(1, nights);
    }

    // ── Création ────────────────────────────────────────────────────────────────
    public Invoice create(InvoiceDto dto) {
        if (dto.getPatientId() == null) {
            throw new IllegalArgumentException("Le patient est obligatoire");
        }
        Patient patient = patientRepository.findByIdAndDeletedAtIsNull(dto.getPatientId())
                .orElseThrow(() -> new IllegalArgumentException("Patient introuvable : " + dto.getPatientId()));

        Invoice inv = new Invoice();
        inv.setPatient(patient);
        inv.setInvoiceNumber(nextNumber());
        inv.setCreatedBy(currentUser());
        inv.setStatus("EN_ATTENTE");
        inv.setPaidAmount(BigDecimal.ZERO);
        applyHeader(dto, inv);
        replaceItems(inv, dto.getItems());
        recompute(inv);
        return invoiceRepository.save(inv);
    }

    // ── Modification (uniquement EN_ATTENTE) ─────────────────────────────────────
    public Invoice update(Long id, InvoiceDto dto) {
        Invoice inv = invoiceRepository.findWithRefsById(id)
                .orElseThrow(() -> new IllegalArgumentException("Facture introuvable : " + id));
        if (!"EN_ATTENTE".equals(inv.getStatus())) {
            throw new IllegalStateException("Seule une facture en attente (sans paiement) peut être modifiée");
        }
        applyHeader(dto, inv);
        replaceItems(inv, dto.getItems());
        recompute(inv);
        return invoiceRepository.save(inv);
    }

    private void applyHeader(InvoiceDto dto, Invoice inv) {
        inv.setDueDate(dto.getDueDate());
        inv.setNotes(dto.getNotes());

        // Assurance : référence + taux de couverture (repli sur le taux par défaut de l'assureur).
        if (dto.getInsuranceId() != null) {
            InsuranceProvider insurer = insuranceProviderRepository.findById(dto.getInsuranceId())
                    .orElseThrow(() -> new IllegalArgumentException("Assureur introuvable : " + dto.getInsuranceId()));
            inv.setInsurance(insurer);
            BigDecimal coverage = dto.getInsuranceCoveragePercent() != null
                    ? dto.getInsuranceCoveragePercent()
                    : insurer.getCoveragePercent();
            inv.setInsuranceCoveragePercent(clampPercent(coverage));
        } else {
            inv.setInsurance(null);
            inv.setInsuranceCoveragePercent(clampPercent(dto.getInsuranceCoveragePercent()));
        }

        // Liens amont (facultatifs) — fixés à la création, conservés ensuite.
        if (inv.getConsultation() == null && dto.getConsultationId() != null) {
            consultationRepository.findById(dto.getConsultationId()).ifPresent(inv::setConsultation);
        }
        if (inv.getHospitalization() == null && dto.getHospitalizationId() != null) {
            hospitalizationRepository.findById(dto.getHospitalizationId()).ifPresent(inv::setHospitalization);
        }
    }

    /** Replace the full line list (orphanRemoval clears the old ones). Blank rows skipped. */
    private void replaceItems(Invoice inv, List<InvoiceItemDto> items) {
        inv.getItems().clear();
        if (items != null) {
            for (InvoiceItemDto in : items) {
                if (in == null) continue;
                boolean blank = (in.getDescription() == null || in.getDescription().isBlank()) && in.getActId() == null;
                if (blank) continue;

                InvoiceItem item = new InvoiceItem();
                ActCatalog act = null;
                if (in.getActId() != null) {
                    act = actCatalogRepository.findById(in.getActId()).orElse(null);
                    item.setAct(act);
                }
                String description = in.getDescription() != null && !in.getDescription().isBlank()
                        ? in.getDescription().trim()
                        : (act != null ? act.getName() : null);
                if (description == null) continue; // ni libellé ni acte → ligne ignorée
                item.setDescription(description);

                int qty = in.getQuantity() > 0 ? in.getQuantity() : 1;
                BigDecimal unit = in.getUnitPrice() != null ? in.getUnitPrice()
                        : (act != null ? act.getPrice() : BigDecimal.ZERO);
                if (unit.signum() < 0) {
                    throw new IllegalArgumentException("Le prix unitaire ne peut pas être négatif");
                }
                item.setQuantity(qty);
                item.setUnitPrice(unit.setScale(2, RoundingMode.HALF_UP));
                item.setTotalPrice(unit.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP));
                inv.addItem(item);
            }
        }
        if (inv.getItems().isEmpty()) {
            throw new IllegalArgumentException("La facture doit contenir au moins une ligne");
        }
    }

    /** Recompute subtotal/insurance/patient amounts from the lines + coverage %. */
    private void recompute(Invoice inv) {
        BigDecimal subtotal = inv.getItems().stream()
                .map(InvoiceItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal coverage = inv.getInsuranceCoveragePercent() != null
                ? inv.getInsuranceCoveragePercent() : BigDecimal.ZERO;
        BigDecimal insuranceAmount = subtotal.multiply(coverage)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal patientAmount = subtotal.subtract(insuranceAmount).setScale(2, RoundingMode.HALF_UP);

        inv.setSubtotal(subtotal);
        inv.setInsuranceAmount(insuranceAmount);
        inv.setPatientAmount(patientAmount);
        refreshStatus(inv);
    }

    // ── Encaissement ──────────────────────────────────────────────────────────────
    public Invoice recordPayment(Long id, PaymentDto dto) {
        Invoice inv = invoiceRepository.findWithRefsById(id)
                .orElseThrow(() -> new IllegalArgumentException("Facture introuvable : " + id));
        if ("ANNULE".equals(inv.getStatus())) {
            throw new IllegalStateException("Une facture annulée ne peut pas être encaissée");
        }
        if ("PAYE".equals(inv.getStatus())) {
            throw new IllegalStateException("Cette facture est déjà soldée");
        }
        if (dto.getAmount() == null || dto.getAmount().signum() <= 0) {
            throw new IllegalArgumentException("Le montant du paiement doit être supérieur à 0");
        }
        if (dto.getMethod() == null || dto.getMethod().isBlank()) {
            throw new IllegalArgumentException("Le mode de paiement est obligatoire");
        }
        BigDecimal amount = dto.getAmount().setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(inv.getBalanceDue()) > 0) {
            throw new IllegalArgumentException("Le montant dépasse le reste à payer ("
                    + inv.getBalanceDue().toPlainString() + ")");
        }

        Payment payment = new Payment();
        payment.setAmount(amount);
        payment.setMethod(dto.getMethod().trim());
        payment.setReference(dto.getReference());
        payment.setNotes(dto.getNotes());
        payment.setPaidAt(LocalDateTime.now());
        payment.setCashier(currentUser());
        inv.addPayment(payment);

        inv.setPaidAmount(inv.getPaidAmount().add(amount).setScale(2, RoundingMode.HALF_UP));
        refreshStatus(inv);
        return invoiceRepository.save(inv);
    }

    // ── Annulation (avec motif) ────────────────────────────────────────────────────
    public Invoice cancel(Long id, String reason) {
        Invoice inv = getById(id);
        if ("PAYE".equals(inv.getStatus())) {
            throw new IllegalStateException("Une facture soldée ne peut pas être annulée (établir un avoir)");
        }
        if ("ANNULE".equals(inv.getStatus())) {
            throw new IllegalStateException("Cette facture est déjà annulée");
        }
        inv.setStatus("ANNULE");
        String motif = reason != null && !reason.isBlank() ? reason.trim() : "Annulation";
        String existing = inv.getNotes() != null && !inv.getNotes().isBlank() ? inv.getNotes() + "\n" : "";
        inv.setNotes(existing + "[ANNULÉE] " + motif);
        return invoiceRepository.save(inv);
    }

    /** Status follows the money: paid≥patientAmount → PAYE, paid>0 → PARTIEL, else EN_ATTENTE. */
    private void refreshStatus(Invoice inv) {
        if ("ANNULE".equals(inv.getStatus())) return;
        BigDecimal paid = inv.getPaidAmount() != null ? inv.getPaidAmount() : BigDecimal.ZERO;
        if (paid.signum() <= 0) {
            inv.setStatus("EN_ATTENTE");
        } else if (paid.compareTo(inv.getPatientAmount()) >= 0) {
            inv.setStatus("PAYE");
        } else {
            inv.setStatus("PARTIEL");
        }
    }

    // ── Tableau de bord & rapports ─────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public BillingDashboardDto dashboard() {
        BillingDashboardDto d = new BillingDashboardDto();
        d.setPendingCount(invoiceRepository.countByStatus("EN_ATTENTE"));
        d.setPartialCount(invoiceRepository.countByStatus("PARTIEL"));
        d.setPaidCount(invoiceRepository.countByStatus("PAYE"));
        BigDecimal invoiced = invoiceRepository.totalInvoiced();
        BigDecimal collected = invoiceRepository.totalCollected();
        d.setTotalInvoiced(invoiced);
        d.setTotalCollected(collected);
        d.setTotalOutstanding(invoiced.subtract(collected).max(BigDecimal.ZERO));
        LocalDate today = LocalDate.now();
        d.setTodayCollected(invoiceRepository.collectedBetween(
                today.atStartOfDay(), today.plusDays(1).atStartOfDay()));
        return d;
    }

    @Transactional(readOnly = true)
    public DailyCashReportDto dailyReport(LocalDate day) {
        LocalDate d = day != null ? day : LocalDate.now();
        List<Payment> payments = paymentRepository
                .findByPaidAtGreaterThanEqualAndPaidAtLessThanOrderByPaidAtAsc(
                        d.atStartOfDay(), d.plusDays(1).atStartOfDay());
        DailyCashReportDto report = new DailyCashReportDto();
        report.setDay(d);
        report.setPaymentCount(payments.size());
        BigDecimal total = BigDecimal.ZERO;
        for (Payment p : payments) {
            total = total.add(p.getAmount());
            report.getTotalByMethod().merge(p.getMethod(), p.getAmount(), BigDecimal::add);
        }
        report.setTotal(total);
        report.setPayments(payments.stream().map(this::toPaymentDto).toList());
        return report;
    }

    // ── Numérotation FAC-YYYY-NNNNN ────────────────────────────────────────────────
    private String nextNumber() {
        ClinicConfig config = clinicConfigService.getConfig();
        String prefix = config.getInvoicePrefix() + "-" + Year.now().getValue() + "-";
        int next = invoiceRepository.findMaxSequence(prefix, prefix.length() + 1) + 1;
        return prefix + String.format("%05d", next);
    }

    private BigDecimal clampPercent(BigDecimal pct) {
        if (pct == null) return BigDecimal.ZERO;
        if (pct.signum() < 0) return BigDecimal.ZERO;
        BigDecimal hundred = BigDecimal.valueOf(100);
        return pct.compareTo(hundred) > 0 ? hundred : pct;
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return userRepository.findByUsername(auth.getName()).orElse(null);
    }

    // ── DTO depuis entité (associations initialisées requises) ──────────────────────
    public InvoiceDto toDto(Invoice inv) {
        InvoiceDto dto = new InvoiceDto();
        dto.setId(inv.getId());
        dto.setInvoiceNumber(inv.getInvoiceNumber());
        dto.setConsultationId(inv.getConsultation() != null ? inv.getConsultation().getId() : null);
        dto.setHospitalizationId(inv.getHospitalization() != null ? inv.getHospitalization().getId() : null);
        dto.setInsuranceCoveragePercent(inv.getInsuranceCoveragePercent());
        dto.setSubtotal(inv.getSubtotal());
        dto.setInsuranceAmount(inv.getInsuranceAmount());
        dto.setPatientAmount(inv.getPatientAmount());
        dto.setPaidAmount(inv.getPaidAmount());
        dto.setBalanceDue(inv.getBalanceDue());
        dto.setStatus(inv.getStatus());
        dto.setDueDate(inv.getDueDate());
        dto.setNotes(inv.getNotes());
        dto.setCreatedAt(inv.getCreatedAt());
        if (inv.getPatient() != null) {
            dto.setPatientId(inv.getPatient().getId());
            dto.setPatientName(inv.getPatient().getFullName());
            dto.setPatientRecordNumber(inv.getPatient().getRecordNumber());
        }
        if (inv.getInsurance() != null) {
            dto.setInsuranceId(inv.getInsurance().getId());
            dto.setInsuranceName(inv.getInsurance().getName());
        }
        dto.setCreatedByName(inv.getCreatedBy() != null ? inv.getCreatedBy().getFullName() : null);

        for (InvoiceItem it : inv.getItems()) {
            InvoiceItemDto idto = new InvoiceItemDto();
            idto.setId(it.getId());
            idto.setDescription(it.getDescription());
            idto.setQuantity(it.getQuantity());
            idto.setUnitPrice(it.getUnitPrice());
            idto.setTotalPrice(it.getTotalPrice());
            if (it.getAct() != null) {
                idto.setActId(it.getAct().getId());
                idto.setActCode(it.getAct().getCode());
            }
            dto.getItems().add(idto);
        }
        for (Payment p : inv.getPayments()) {
            dto.getPayments().add(toPaymentDto(p));
        }
        return dto;
    }

    private PaymentDto toPaymentDto(Payment p) {
        PaymentDto pdto = new PaymentDto();
        pdto.setId(p.getId());
        pdto.setAmount(p.getAmount());
        pdto.setMethod(p.getMethod());
        pdto.setReference(p.getReference());
        pdto.setPaidAt(p.getPaidAt());
        pdto.setNotes(p.getNotes());
        pdto.setCashierName(p.getCashier() != null ? p.getCashier().getFullName() : null);
        if (p.getInvoice() != null) {
            pdto.setInvoiceId(p.getInvoice().getId());
            pdto.setInvoiceNumber(p.getInvoice().getInvoiceNumber());
            if (p.getInvoice().getPatient() != null) {
                pdto.setPatientName(p.getInvoice().getPatient().getFullName());
            }
        }
        return pdto;
    }
}
