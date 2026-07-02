package com.clinic.backend.export;

import com.clinic.backend.dto.AppointmentDto;
import com.clinic.backend.dto.DispensationDto;
import com.clinic.backend.dto.DispensationItemDto;
import com.clinic.backend.dto.ExportPreviewDto;
import com.clinic.backend.dto.InvoiceDto;
import com.clinic.backend.patient.Patient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Export Excel des <b>fiches / listes opérationnelles</b> (factures, patients, RDV,
 * dispensations) avec <b>choix des colonnes</b> et <b>aperçu avant téléchargement</b>.
 * <p>
 * Chaque fiche est décrite par une liste de colonnes {@link Col} (clé stable + libellé +
 * extracteur de valeur). Un même moteur générique produit soit l'aperçu
 * ({@link #preview}) soit le classeur ({@link #xlsx}) en ne gardant que les colonnes
 * sélectionnées. Les appelants fournissent des DTO/entités déjà chargés (scalaires
 * uniquement — OSIV-safe).
 */
@Service
@RequiredArgsConstructor
public class FicheExportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final int SAMPLE = 12;

    private final ExcelExportService excel;

    /** Colonne exportable : clé stable (pour la sélection), libellé, extracteur de valeur. */
    public record Col<T>(String key, String label, Function<T, Object> value) {}

    // ── Définitions des colonnes par fiche ────────────────────────────────────────
    public static final List<Col<InvoiceDto>> INVOICE_COLS = List.of(
            new Col<>("number", "N° facture", InvoiceDto::getInvoiceNumber),
            new Col<>("date", "Émise le", i -> fmt(i.getCreatedAt())),
            new Col<>("patient", "Patient", InvoiceDto::getPatientName),
            new Col<>("record", "N° dossier", InvoiceDto::getPatientRecordNumber),
            new Col<>("insurance", "Assurance", InvoiceDto::getInsuranceName),
            new Col<>("subtotal", "Sous-total", InvoiceDto::getSubtotal),
            new Col<>("insuranceAmount", "Part assurance", InvoiceDto::getInsuranceAmount),
            new Col<>("patientAmount", "Part patient", InvoiceDto::getPatientAmount),
            new Col<>("paid", "Payé", InvoiceDto::getPaidAmount),
            new Col<>("balance", "Reste à payer", InvoiceDto::getBalanceDue),
            new Col<>("status", "Statut", InvoiceDto::getStatus));

    public static final List<Col<Patient>> PATIENT_COLS = List.of(
            new Col<>("record", "N° dossier", Patient::getRecordNumber),
            new Col<>("lastName", "Nom", Patient::getLastName),
            new Col<>("firstName", "Prénom", Patient::getFirstName),
            new Col<>("gender", "Sexe", Patient::getGender),
            new Col<>("birthDate", "Date de naissance", p -> fmt(p.getBirthDate())),
            new Col<>("phone", "Téléphone", Patient::getPhone),
            new Col<>("bloodType", "Groupe sanguin", Patient::getBloodType),
            new Col<>("insurance", "N° assurance", Patient::getInsuranceNumber),
            new Col<>("created", "Créé le", p -> fmt(p.getCreatedAt())));

    public static final List<Col<AppointmentDto>> APPOINTMENT_COLS = List.of(
            new Col<>("datetime", "Date & heure", a -> fmt(a.getStartTime())),
            new Col<>("patient", "Patient", AppointmentDto::getPatientName),
            new Col<>("doctor", "Médecin", AppointmentDto::getDoctorName),
            new Col<>("department", "Service", AppointmentDto::getDepartmentName),
            new Col<>("type", "Type", AppointmentDto::getType),
            new Col<>("reason", "Motif", AppointmentDto::getReason),
            new Col<>("status", "Statut", AppointmentDto::getStatus));

    /** Ligne à plat d'une dispensation (une ligne par article délivré). */
    public record DispLine(DispensationDto d, DispensationItemDto it) {}

    public static final List<Col<DispLine>> DISPENSATION_COLS = List.of(
            new Col<>("date", "Date", l -> fmt(l.d().getDispensedAt())),
            new Col<>("prescription", "N° ordonnance", l -> l.d().getPrescriptionNumber()),
            new Col<>("patient", "Patient", l -> l.d().getPatientName()),
            new Col<>("record", "N° dossier", l -> l.d().getPatientRecordNumber()),
            new Col<>("drug", "Médicament", l -> l.it() != null ? l.it().getDrugName() : ""),
            new Col<>("batch", "Lot", l -> l.it() != null ? l.it().getBatchNumber() : ""),
            new Col<>("qty", "Quantité", l -> l.it() != null ? l.it().getQuantity() : null),
            new Col<>("unitPrice", "Prix unitaire", l -> l.it() != null ? l.it().getUnitPrice() : null),
            new Col<>("lineTotal", "Total ligne", l -> l.it() != null ? l.it().getTotalPrice() : l.d().getTotalAmount()),
            new Col<>("pharmacist", "Pharmacien", l -> l.d().getPharmacistName()));

    /** Aplati les dispensations en lignes par article (une ligne vide si sans article). */
    public static List<DispLine> flattenDispensations(List<DispensationDto> ds) {
        List<DispLine> lines = new ArrayList<>();
        for (DispensationDto d : ds) {
            if (d.getItems() == null || d.getItems().isEmpty()) {
                lines.add(new DispLine(d, null));
            } else {
                for (DispensationItemDto it : d.getItems()) lines.add(new DispLine(d, it));
            }
        }
        return lines;
    }

    // ── Moteur générique : aperçu + classeur ──────────────────────────────────────

    /** Construit l'aperçu (colonnes disponibles + échantillon des colonnes retenues). */
    public <T> ExportPreviewDto preview(String title, String previewUrl, String downloadUrl,
                                        java.util.Map<String, String> context, List<Col<T>> allCols,
                                        List<T> data, Set<String> selectedKeys) {
        List<Col<T>> cols = selectedCols(allCols, selectedKeys);
        ExportPreviewDto p = new ExportPreviewDto();
        p.setTitle(title);
        p.setPreviewUrl(previewUrl);
        p.setDownloadUrl(downloadUrl);
        if (context != null) {
            context.forEach((k, v) -> { if (v != null && !v.isBlank()) p.getContext().put(k, v); });
        }
        Set<String> effective = keysOf(cols);
        for (Col<T> c : allCols) {
            p.getFields().add(new ExportPreviewDto.Field(c.key(), c.label(), effective.contains(c.key())));
        }
        p.setSampleHeaders(cols.stream().map(Col::label).toList());
        for (T row : data.stream().limit(SAMPLE).toList()) {
            p.getSampleRows().add(cols.stream().map(c -> safe(c.value().apply(row))).toList());
        }
        p.setTotalRows(data.size());
        p.setSampleSize(Math.min(SAMPLE, data.size()));
        return p;
    }

    /** Produit le .xlsx en ne gardant que les colonnes sélectionnées. */
    public <T> byte[] xlsx(String sheet, List<Col<T>> allCols, List<T> data, Set<String> selectedKeys) {
        List<Col<T>> cols = selectedCols(allCols, selectedKeys);
        List<String> headers = cols.stream().map(Col::label).toList();
        List<List<Object>> rows = new ArrayList<>();
        for (T row : data) {
            rows.add(cols.stream().map(c -> c.value().apply(row)).toList());
        }
        return excel.toXlsx(sheet, headers, rows);
    }

    /** Colonnes retenues dans l'ordre défini ; si la sélection est vide/null → toutes. */
    private static <T> List<Col<T>> selectedCols(List<Col<T>> allCols, Set<String> selectedKeys) {
        if (selectedKeys == null || selectedKeys.isEmpty()) return allCols;
        List<Col<T>> out = new ArrayList<>();
        for (Col<T> c : allCols) if (selectedKeys.contains(c.key())) out.add(c);
        return out.isEmpty() ? allCols : out; // jamais zéro colonne
    }

    private static <T> Set<String> keysOf(List<Col<T>> cols) {
        Set<String> s = new LinkedHashSet<>();
        for (Col<T> c : cols) s.add(c.key());
        return s;
    }

    private static Object safe(Object v) {
        return v != null ? v : "";
    }

    private static String fmt(LocalDate d) {
        return d != null ? d.format(DATE) : "";
    }

    private static String fmt(LocalDateTime d) {
        return d != null ? d.format(DATETIME) : "";
    }
}
