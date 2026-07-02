package com.clinic.backend.export;

import com.clinic.backend.clinicconfig.ClinicConfigService;
import com.clinic.backend.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mapping centralisé « rapport → document » pour les exports PDF / Excel (D4a).
 * <p>
 * Réutilisé à la fois par {@code ReportWebController} (boutons d'export des vues web)
 * et par {@code ReportApiController} ({@code ?format=pdf|excel} pour les clients
 * API / desktop), pour éviter de dupliquer la construction des KPI / sections.
 * Le PDF passe par le template d'impression générique {@code reports/pdf-report} ;
 * l'Excel par {@link ExcelExportService}.
 * <p>
 * Les libellés de ces documents officiels restent en FR (cf. {@code docs/I18N-PLAN.md}).
 */
@Service
@RequiredArgsConstructor
public class ReportExportService {

    private static final Locale FR = Locale.FRENCH;

    private final PdfExportService pdfExportService;
    private final ExcelExportService excelExportService;
    private final ClinicConfigService clinicConfigService;

    // ── PDF ──────────────────────────────────────────────────────────────────────

    public byte[] monthlyFinancialPdf(MonthlyFinancialReportDto r, int month, int year) {
        String cur = currency();
        List<Map<String, Object>> kpis = List.of(
                kpi("Facturé (part patient)", money(r.getTotalInvoiced(), cur)),
                kpi("Encaissé", money(r.getTotalCollected(), cur)),
                kpi("Reste à recouvrer", money(r.getTotalOutstanding(), cur)),
                kpi("Nombre de factures", r.getInvoiceCount()));
        List<Map<String, Object>> rows = new ArrayList<>();
        r.getCollectedByMethod().forEach((m, a) -> rows.add(row(m, money(a, cur))));
        List<Map<String, Object>> sections = List.of(
                section("Encaissements par mode de paiement", "Mode", "Montant", rows));
        return reportPdf("Bilan financier", period(month, year), kpis, sections);
    }

    public byte[] activityPdf(ActivityReportDto r, int month, int year) {
        List<Map<String, Object>> kpis = List.of(
                kpi("Consultations", r.getConsultations()),
                kpi("Rendez-vous", r.getAppointments()),
                kpi("Nouveaux patients", r.getNewPatients()),
                kpi("Demandes de laboratoire", r.getLabRequests()),
                kpi("Admissions", r.getAdmissions()));
        List<Map<String, Object>> sections = List.of(
                section("Consultations par département", "Département", "Nombre",
                        rowsOf(r.getConsultationsByDepartment())));
        return reportPdf("Rapport d'activité", period(month, year), kpis, sections);
    }

    public byte[] epidemiologyPdf(EpidemiologyReportDto r, int month, int year) {
        List<Map<String, Object>> kpis = List.of(
                kpi("Consultations sur la période", r.getTotalConsultations()));
        List<Map<String, Object>> sections = List.of(
                section("Principales pathologies", "Pathologie", "Cas", rowsOf(r.getTopPathologies())),
                section("Répartition par tranche d'âge", "Tranche", "Patients", rowsOf(r.getByAgeGroup())),
                section("Répartition par sexe", "Sexe", "Patients", rowsOf(r.getBySex())),
                section("Répartition par département", "Département", "Consultations", rowsOf(r.getByDepartment())));
        return reportPdf("Statistiques épidémiologiques", period(month, year), kpis, sections);
    }

    public byte[] outstandingPdf(OutstandingReportDto r) {
        String cur = currency();
        List<Map<String, Object>> kpis = List.of(
                kpi("Factures impayées", r.getInvoiceCount()),
                kpi("Reste à recouvrer", money(r.getTotalOutstanding(), cur)));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (InvoiceDto inv : r.getInvoices()) {
            rows.add(row(inv.getInvoiceNumber() + " — " + inv.getPatientName(),
                    money(inv.getBalanceDue(), cur)));
        }
        List<Map<String, Object>> sections = List.of(
                section("Détail des impayés", "Facture / Patient", "Reste à payer", rows));
        return reportPdf("Liste des impayés", "Au " + LocalDate.now(), kpis, sections);
    }

    public byte[] dailyCashPdf(DailyCashReportDto r) {
        String cur = currency();
        List<Map<String, Object>> kpis = List.of(
                kpi("Nombre de paiements", r.getPaymentCount()),
                kpi("Total encaissé", money(r.getTotal(), cur)));
        List<Map<String, Object>> rows = new ArrayList<>();
        r.getTotalByMethod().forEach((m, a) -> rows.add(row(m, money(a, cur))));
        List<Map<String, Object>> sections = List.of(
                section("Encaissements par mode de paiement", "Mode", "Montant", rows));
        return reportPdf("Caisse du jour", r.getDay() != null ? r.getDay().toString() : "", kpis, sections);
    }

    public byte[] stockPdf(List<StockItemDto> stock) {
        List<Map<String, Object>> kpis = List.of(
                kpi("Lots en stock", stock.size()),
                kpi("Lots en alerte", stock.stream().filter(StockItemDto::isLow).count()),
                kpi("Lots périmés", stock.stream().filter(StockItemDto::isExpired).count()));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (StockItemDto s : stock) {
            rows.add(row(s.getDrugName() + " (lot " + s.getBatchNumber() + ")",
                    String.valueOf(s.getQuantity())));
        }
        List<Map<String, Object>> sections = List.of(
                section("État des lots", "Médicament (lot)", "Quantité", rows));
        return reportPdf("État du stock", "Au " + LocalDate.now(), kpis, sections);
    }

    // ── Excel ────────────────────────────────────────────────────────────────────

    public byte[] monthlyFinancialExcel(MonthlyFinancialReportDto r) {
        List<List<Object>> kpis = new ArrayList<>();
        kpis.add(Arrays.asList("Facturé (part patient)", r.getTotalInvoiced()));
        kpis.add(Arrays.asList("Encaissé", r.getTotalCollected()));
        kpis.add(Arrays.asList("Reste à recouvrer", r.getTotalOutstanding()));
        kpis.add(Arrays.asList("Nombre de factures", r.getInvoiceCount()));
        List<List<Object>> byMethod = new ArrayList<>();
        r.getCollectedByMethod().forEach((m, a) -> byMethod.add(Arrays.asList(m, a)));
        return excelExportService.toSectionsXlsx("Bilan financier", List.of(
                new ExcelExportService.Section("Indicateurs", List.of("Indicateur", "Montant"), kpis),
                new ExcelExportService.Section("Encaissements par mode de paiement", List.of("Mode", "Montant"), byMethod)));
    }

    public byte[] activityExcel(ActivityReportDto r) {
        List<List<Object>> kpis = new ArrayList<>();
        kpis.add(Arrays.asList("Consultations", r.getConsultations()));
        kpis.add(Arrays.asList("Rendez-vous", r.getAppointments()));
        kpis.add(Arrays.asList("Nouveaux patients", r.getNewPatients()));
        kpis.add(Arrays.asList("Demandes de laboratoire", r.getLabRequests()));
        kpis.add(Arrays.asList("Admissions", r.getAdmissions()));
        return excelExportService.toSectionsXlsx("Activité", List.of(
                new ExcelExportService.Section("Indicateurs", List.of("Indicateur", "Valeur"), kpis),
                new ExcelExportService.Section("Consultations par département",
                        List.of("Département", "Consultations"), countRows(r.getConsultationsByDepartment()))));
    }

    public byte[] epidemiologyExcel(EpidemiologyReportDto r) {
        return excelExportService.toSectionsXlsx("Épidémiologie", List.of(
                new ExcelExportService.Section("Indicateurs", List.of("Indicateur", "Valeur"),
                        List.of(Arrays.asList("Consultations analysées", r.getTotalConsultations()))),
                new ExcelExportService.Section("Principales pathologies",
                        List.of("Pathologie", "Cas"), countRows(r.getTopPathologies())),
                new ExcelExportService.Section("Répartition par tranche d'âge",
                        List.of("Tranche", "Consultations"), countRows(r.getByAgeGroup())),
                new ExcelExportService.Section("Répartition par sexe",
                        List.of("Sexe", "Consultations"), countRows(r.getBySex())),
                new ExcelExportService.Section("Répartition par département",
                        List.of("Département", "Consultations"), countRows(r.getByDepartment()))));
    }

    /** Lignes [label, count] pour une répartition (liste nullable). */
    private static List<List<Object>> countRows(List<LabelValueDto> items) {
        List<List<Object>> rows = new ArrayList<>();
        if (items != null) {
            for (LabelValueDto lv : items) rows.add(Arrays.asList(lv.getLabel(), lv.getCount()));
        }
        return rows;
    }

    public byte[] outstandingExcel(OutstandingReportDto r) {
        List<String> headers = List.of(
                "N° facture", "Patient", "Total", "Payé", "Reste à payer", "Statut", "Émise le");
        List<List<Object>> rows = new ArrayList<>();
        for (InvoiceDto inv : r.getInvoices()) {
            rows.add(Arrays.asList(
                    inv.getInvoiceNumber(),
                    inv.getPatientName(),
                    inv.getPatientAmount(),
                    inv.getPaidAmount(),
                    inv.getBalanceDue(),
                    inv.getStatus(),
                    inv.getCreatedAt() != null ? inv.getCreatedAt().toLocalDate().toString() : ""));
        }
        return excelExportService.toXlsx("Impayés", headers, rows);
    }

    public byte[] dailyCashExcel(DailyCashReportDto r) {
        List<String> headers = List.of("Mode de paiement", "Montant");
        List<List<Object>> rows = new ArrayList<>();
        r.getTotalByMethod().forEach((m, a) -> rows.add(Arrays.asList(m, a)));
        return excelExportService.toXlsx("Caisse du jour", headers, rows);
    }

    public byte[] stockExcel(List<StockItemDto> stock) {
        List<String> headers = List.of(
                "Médicament", "Lot", "Quantité", "Seuil alerte", "Péremption", "Prix de vente", "Statut");
        List<List<Object>> rows = new ArrayList<>();
        for (StockItemDto s : stock) {
            rows.add(Arrays.asList(
                    s.getDrugName(),
                    s.getBatchNumber(),
                    s.getQuantity(),
                    s.getQuantityAlert(),
                    s.getExpiryDate() != null ? s.getExpiryDate().toString() : "",
                    s.getSellingPrice(),
                    s.isExpired() ? "Périmé" : s.isLow() ? "Stock bas" : "OK"));
        }
        return excelExportService.toXlsx("Stock", headers, rows);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private byte[] reportPdf(String title, String period,
                             List<Map<String, Object>> kpis, List<Map<String, Object>> sections) {
        Map<String, Object> model = new HashMap<>();
        model.put("config", clinicConfigService.getConfig());
        model.put("title", title);
        model.put("period", period);
        model.put("kpis", kpis);
        model.put("sections", sections);
        model.put("generatedAt", LocalDate.now().toString());
        return pdfExportService.renderTemplate("reports/pdf-report", model);
    }

    private String currency() {
        String c = clinicConfigService.getConfig().getCurrency();
        return c != null ? c : "";
    }

    private static String period(int month, int year) {
        return Month.of(month).getDisplayName(TextStyle.FULL, FR) + " " + year;
    }

    private static String money(BigDecimal v, String currency) {
        BigDecimal amount = v != null ? v : BigDecimal.ZERO;
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

    private static List<Map<String, Object>> rowsOf(List<LabelValueDto> items) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (items != null) {
            for (LabelValueDto lv : items) {
                rows.add(row(lv.getLabel(), String.valueOf(lv.getCount())));
            }
        }
        return rows;
    }
}
