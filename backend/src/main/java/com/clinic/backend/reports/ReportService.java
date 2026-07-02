package com.clinic.backend.reports;

import com.clinic.backend.appointment.Appointment;
import com.clinic.backend.appointment.AppointmentRepository;
import com.clinic.backend.appointment.AppointmentService;
import com.clinic.backend.billing.BillingService;
import com.clinic.backend.billing.Invoice;
import com.clinic.backend.billing.InvoiceRepository;
import com.clinic.backend.billing.PaymentRepository;
import com.clinic.backend.consultation.Consultation;
import com.clinic.backend.consultation.ConsultationRepository;
import com.clinic.backend.consultation.ConsultationService;
import com.clinic.backend.dto.*;
import com.clinic.backend.hospitalization.HospitalizationRepository;
import com.clinic.backend.hospitalization.RoomRepository;
import com.clinic.backend.lab.LabRequest;
import com.clinic.backend.lab.LabRequestRepository;
import com.clinic.backend.lab.LabService;
import com.clinic.backend.model.User;
import com.clinic.backend.patient.PatientRepository;
import com.clinic.backend.pharmacy.PharmacyService;
import com.clinic.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only reporting layer (module 14). Owns no tables — it aggregates across the
 * existing modules' repositories to feed dashboards and exportable report views.
 * <p>
 * All mapping to DTOs happens inside these {@code readOnly} transactions, so lazy
 * associations resolve even with OSIV disabled (the persistence context is open for
 * the duration of the service call, just not in the view layer).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final ConsultationRepository consultationRepository;
    private final LabRequestRepository labRequestRepository;
    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final HospitalizationRepository hospitalizationRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    private final PharmacyService pharmacyService;
    private final BillingService billingService;
    private final ConsultationService consultationService;
    private final LabService labService;
    private final AppointmentService appointmentService;
    private final com.clinic.backend.catalog.Icd10Service icd10Service;

    // Libellés démographiques (sexe / tranches d'âge) localisés via le bundle ; la locale
    // courante est celle de la requête (cookie clinicLang). Cf. docs/I18N-PLAN.md slice 10.
    private final com.clinic.backend.i18n.WebI18n i18n;

    // ══════════════════════════ TABLEAU DE BORD ADMIN ══════════════════════════

    public AdminDashboardDto adminDashboard() {
        AdminDashboardDto d = new AdminDashboardDto();
        LocalDate today = LocalDate.now();

        // Revenus (encaissements)
        BigDecimal revToday = invoiceRepository.collectedBetween(
                today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate nextMonthStart = monthStart.plusMonths(1);
        LocalDate prevMonthStart = monthStart.minusMonths(1);
        BigDecimal revMonth = invoiceRepository.collectedBetween(
                monthStart.atStartOfDay(), nextMonthStart.atStartOfDay());
        BigDecimal revPrev = invoiceRepository.collectedBetween(
                prevMonthStart.atStartOfDay(), monthStart.atStartOfDay());
        d.setRevenueToday(revToday);
        d.setRevenueMonth(revMonth);
        d.setRevenuePrevMonth(revPrev);
        d.setRevenueMonthVariationPercent(variationPercent(revMonth, revPrev));

        // Activité
        LocalDate weekStart = today.minusDays(6);
        d.setConsultationsToday(consultationRepository.countBetween(
                today.atStartOfDay(), today.plusDays(1).atStartOfDay()));
        d.setConsultationsWeek(consultationRepository.countBetween(
                weekStart.atStartOfDay(), today.plusDays(1).atStartOfDay()));
        d.setConsultationsMonth(consultationRepository.countBetween(
                monthStart.atStartOfDay(), nextMonthStart.atStartOfDay()));
        d.setPatientsTotal(patientRepository.countActive());

        // Occupation des lits
        long occupied = hospitalizationRepository.countByStatus("ADMIS");
        long beds = roomRepository.totalActiveBeds();
        d.setOccupiedBeds(occupied);
        d.setTotalBeds(beds);
        d.setBedOccupancyRate(beds > 0
                ? BigDecimal.valueOf(occupied * 100.0 / beds).setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);

        // Créances (impayés échus) — réutilise l'agrégat du rapport des impayés
        OutstandingReportDto outstanding = outstanding();
        d.setOutstandingTotal(outstanding.getTotalOutstanding());
        d.setOutstandingCount(outstanding.getInvoiceCount());

        // Pharmacie (alertes)
        d.setLowStockCount(pharmacyService.lowStock().size());
        d.setExpiringCount(pharmacyService.expiringStock().size());

        // Top pathologies (mois courant)
        d.setTopPathologies(topDiagnoses(monthStart.atStartOfDay(), nextMonthStart.atStartOfDay(), 5));

        // Répartition des paiements (mois courant)
        d.setPaymentMethodBreakdown(methodBreakdown(
                monthStart.atStartOfDay(), nextMonthStart.atStartOfDay()));
        return d;
    }

    // ══════════════════════════ TABLEAU DE BORD MÉDECIN ════════════════════════

    public DoctorDashboardDto doctorDashboard() {
        DoctorDashboardDto d = new DoctorDashboardDto();
        User doctor = currentUser();
        if (doctor == null) {
            return d; // pas d'utilisateur identifié → tableau vide
        }
        d.setDoctorName(doctor.getFullName());
        LocalDate today = LocalDate.now();

        // Mes consultations du jour
        List<Consultation> todays = consultationRepository.findForDoctorBetween(
                doctor.getId(), today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        d.setTodayConsultations(todays.stream().map(consultationService::toDto).toList());

        // Résultats labo en attente de ma validation
        List<LabRequest> pending = labRequestRepository.findPendingValidationForDoctor(doctor.getId());
        d.setLabPendingValidation(pending.stream().map(labService::toDto).toList());
        d.setLabPendingValidationCount(pending.size());

        // Mes rendez-vous de la semaine (lundi → dimanche relatif : aujourd'hui + 6 jours)
        List<Appointment> week = appointmentRepository.search(
                today.atStartOfDay(), today.plusDays(7).atStartOfDay(), doctor.getId(), null, null);
        d.setWeekAppointments(week.stream().map(appointmentService::toDto).toList());
        return d;
    }

    // ══════════════════════════ TABLEAU DE BORD PHARMACIE ═══════════════════════

    public PharmacyDashboardDto pharmacyDashboard() {
        return pharmacyService.dashboard();
    }

    // ══════════════════════════ RAPPORT CAISSE JOURNALIER ══════════════════════

    public DailyCashReportDto dailyCash(LocalDate day) {
        return billingService.dailyReport(day);
    }

    // ══════════════════════════ BILAN FINANCIER MENSUEL ════════════════════════

    public MonthlyFinancialReportDto monthlyFinancial(int month, int year) {
        MonthlyFinancialReportDto r = new MonthlyFinancialReportDto();
        r.setMonth(month);
        r.setYear(year);
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.plusMonths(1);

        // Facturé : part patient des factures non annulées créées dans le mois.
        List<Invoice> invoices = invoiceRepository.search(
                start.atStartOfDay(), end.atStartOfDay(), null, null);
        BigDecimal invoiced = BigDecimal.ZERO;
        long count = 0;
        for (Invoice inv : invoices) {
            if ("ANNULE".equals(inv.getStatus())) continue;
            invoiced = invoiced.add(inv.getPatientAmount() != null ? inv.getPatientAmount() : BigDecimal.ZERO);
            count++;
        }
        BigDecimal collected = invoiceRepository.collectedBetween(start.atStartOfDay(), end.atStartOfDay());
        r.setInvoiceCount(count);
        r.setTotalInvoiced(invoiced.setScale(2, RoundingMode.HALF_UP));
        r.setTotalCollected(collected);
        r.setTotalOutstanding(invoiced.subtract(collected).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
        r.setCollectedByMethod(methodBreakdown(start.atStartOfDay(), end.atStartOfDay()));
        return r;
    }

    // ══════════════════════════ RAPPORT D'ACTIVITÉ MÉDICALE ════════════════════

    public ActivityReportDto activity(int month, int year) {
        ActivityReportDto r = new ActivityReportDto();
        r.setMonth(month);
        r.setYear(year);
        LocalDateTime from = LocalDate.of(year, month, 1).atStartOfDay();
        LocalDateTime to = LocalDate.of(year, month, 1).plusMonths(1).atStartOfDay();

        r.setConsultations(consultationRepository.countBetween(from, to));
        r.setAppointments(appointmentRepository.countBetween(from, to));
        r.setNewPatients(patientRepository.countNewBetween(from, to));
        r.setAdmissions(hospitalizationRepository.countAdmissionsBetween(from, to));
        // Demandes labo du mois : pas de countBetween dédié → recherche filtrée par période.
        r.setLabRequests(labRequestRepository.search(from, to, null, null, null, null).size());

        r.setConsultationsByDepartment(toLabelCounts(
                consultationRepository.countByDepartmentBetween(from, to)));
        return r;
    }

    // ══════════════════════════ STATISTIQUES ÉPIDÉMIOLOGIQUES ══════════════════

    public EpidemiologyReportDto epidemiology(int month, int year) {
        EpidemiologyReportDto r = new EpidemiologyReportDto();
        r.setMonth(month);
        r.setYear(year);
        LocalDateTime from = LocalDate.of(year, month, 1).atStartOfDay();
        LocalDateTime to = LocalDate.of(year, month, 1).plusMonths(1).atStartOfDay();

        r.setTotalConsultations(consultationRepository.countBetween(from, to));
        r.setTopPathologies(topDiagnoses(from, to, 10));
        r.setByDepartment(toLabelCounts(consultationRepository.countByDepartmentBetween(from, to)));
        r.setBySex(sexLabels(consultationRepository.countBySexBetween(from, to)));
        r.setByAgeGroup(ageGroups(consultationRepository.findConsultationPatientBirthDates(from, to)));
        return r;
    }

    // ══════════════════════════ LISTE DES IMPAYÉS ══════════════════════════════

    public OutstandingReportDto outstanding() {
        OutstandingReportDto r = new OutstandingReportDto();
        List<Invoice> unpaid = invoiceRepository.findOverdueUnpaid(LocalDateTime.now());
        BigDecimal total = BigDecimal.ZERO;
        for (Invoice inv : unpaid) {
            InvoiceDto dto = billingService.toDto(inv);
            r.getInvoices().add(dto);
            if (dto.getBalanceDue() != null) {
                total = total.add(dto.getBalanceDue());
            }
        }
        r.setInvoiceCount(unpaid.size());
        r.setTotalOutstanding(total.setScale(2, RoundingMode.HALF_UP));
        return r;
    }

    // ══════════════════════════ ÉTAT DU STOCK ══════════════════════════════════

    public List<StockItemDto> stock() {
        return pharmacyService.listStock();
    }

    // ════════════════════════════════ HELPERS ══════════════════════════════════

    private List<LabelValueDto> topDiagnoses(LocalDateTime from, LocalDateTime to, int limit) {
        // D4c : « top pathologies » agrégées sur les codes CIM-10 (structurés, non chiffrés)
        // et non plus sur le diagnostic en texte libre. Une consultation peut porter plusieurs
        // codes (séparés par virgule) → chaque code est compté séparément.
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String raw : consultationRepository.findCompletedIcd10Codes(from, to)) {
            for (String code : com.clinic.backend.catalog.Icd10Service.splitCodes(raw)) {
                counts.merge(code, 1L, Long::sum);
            }
        }
        if (counts.isEmpty()) {
            return List.of();
        }
        // Top N codes, puis résolution des libellés en un seul lot (« J06.9 — Infection… »).
        List<Map.Entry<String, Long>> top = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .toList();
        Map<String, String> titles = icd10Service.titlesByCode(
                top.stream().map(Map.Entry::getKey).toList());
        return top.stream()
                .map(e -> new LabelValueDto(
                        com.clinic.backend.catalog.Icd10Service.displayLabel(e.getKey(), titles.get(e.getKey())),
                        e.getValue()))
                .toList();
    }

    private List<LabelValueDto> toLabelCounts(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new LabelValueDto(
                        row[0] != null ? row[0].toString() : i18n.t("gender.unknown"),
                        ((Number) row[1]).longValue()))
                .toList();
    }

    private List<LabelValueDto> sexLabels(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new LabelValueDto(genderLabel((String) row[0]), ((Number) row[1]).longValue()))
                .toList();
    }

    private String genderLabel(String g) {
        if (g == null) return i18n.t("gender.unknown");
        return switch (g) {
            case "M" -> i18n.t("gender.M");
            case "F" -> i18n.t("gender.F");
            default -> g;
        };
    }

    /** Encaissements par mode de paiement sur la période, ordre décroissant préservé. */
    private Map<String, BigDecimal> methodBreakdown(LocalDateTime from, LocalDateTime to) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        for (Object[] row : paymentRepository.sumByMethodBetween(from, to)) {
            map.put((String) row[0], (BigDecimal) row[1]);
        }
        return map;
    }

    /** Découpe des âges en tranches standards (0-4, 5-14, 15-44, 45-64, 65+). */
    private List<LabelValueDto> ageGroups(List<LocalDate> birthDates) {
        long[] buckets = new long[6]; // [0-4,5-14,15-44,45-64,65+,inconnu]
        LocalDate today = LocalDate.now();
        for (LocalDate bd : birthDates) {
            if (bd == null) { buckets[5]++; continue; }
            int age = Period.between(bd, today).getYears();
            if (age < 5) buckets[0]++;
            else if (age < 15) buckets[1]++;
            else if (age < 45) buckets[2]++;
            else if (age < 65) buckets[3]++;
            else buckets[4]++;
        }
        List<LabelValueDto> out = new java.util.ArrayList<>();
        out.add(new LabelValueDto(i18n.t("reports.age.0_4"), buckets[0]));
        out.add(new LabelValueDto(i18n.t("reports.age.5_14"), buckets[1]));
        out.add(new LabelValueDto(i18n.t("reports.age.15_44"), buckets[2]));
        out.add(new LabelValueDto(i18n.t("reports.age.45_64"), buckets[3]));
        out.add(new LabelValueDto(i18n.t("reports.age.65_plus"), buckets[4]));
        if (buckets[5] > 0) out.add(new LabelValueDto(i18n.t("reports.age.unknown"), buckets[5]));
        return out;
    }

    private BigDecimal variationPercent(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.signum() == 0) return null;
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 1, RoundingMode.HALF_UP);
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return userRepository.findByUsername(auth.getName()).orElse(null);
    }
}
