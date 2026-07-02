package com.clinic.backend.export;

import com.clinic.backend.dto.AppointmentDto;
import com.clinic.backend.dto.DispensationDto;
import com.clinic.backend.dto.DispensationItemDto;
import com.clinic.backend.dto.InvoiceDto;
import com.clinic.backend.patient.Patient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Export Excel des <b>fiches / listes opérationnelles</b> (chantier B) : journal des
 * factures, registre patients, agenda des rendez-vous, journal des dispensations.
 * <p>
 * Diffère de {@link ReportExportService} (qui exporte des <i>rapports agrégés</i>) :
 * ici on sort les lignes détaillées d'une liste de travail, pour la compta / le registre.
 * Réutilise {@link ExcelExportService} ; l'appelant fournit des DTO/entités déjà chargés
 * (mapping de scalaires uniquement — aucune association lazy touchée, OSIV est OFF).
 */
@Service
@RequiredArgsConstructor
public class FicheExportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ExcelExportService excel;

    // ── Journal des factures / encaissements (compta) ─────────────────────────────
    public byte[] invoicesXlsx(List<InvoiceDto> invoices) {
        List<String> headers = List.of(
                "N° facture", "Émise le", "Patient", "N° dossier", "Assurance",
                "Sous-total", "Part assurance", "Part patient", "Payé", "Reste à payer", "Statut");
        List<List<Object>> rows = new ArrayList<>();
        for (InvoiceDto inv : invoices) {
            rows.add(Arrays.asList(
                    inv.getInvoiceNumber(),
                    fmt(inv.getCreatedAt()),
                    inv.getPatientName(),
                    inv.getPatientRecordNumber(),
                    inv.getInsuranceName(),
                    inv.getSubtotal(),
                    inv.getInsuranceAmount(),
                    inv.getPatientAmount(),
                    inv.getPaidAmount(),
                    inv.getBalanceDue(),
                    inv.getStatus()));
        }
        return excel.toXlsx("Factures", headers, rows);
    }

    // ── Registre patients ─────────────────────────────────────────────────────────
    public byte[] patientsXlsx(List<Patient> patients) {
        List<String> headers = List.of(
                "N° dossier", "Nom", "Prénom", "Sexe", "Date de naissance",
                "Téléphone", "Groupe sanguin", "N° assurance", "Créé le");
        List<List<Object>> rows = new ArrayList<>();
        for (Patient p : patients) {
            rows.add(Arrays.asList(
                    p.getRecordNumber(),
                    p.getLastName(),
                    p.getFirstName(),
                    p.getGender(),
                    fmt(p.getBirthDate()),
                    p.getPhone(),
                    p.getBloodType(),
                    p.getInsuranceNumber(),
                    fmt(p.getCreatedAt())));
        }
        return excel.toXlsx("Patients", headers, rows);
    }

    // ── Agenda des rendez-vous ──────────────────────────────────────────────────────
    public byte[] appointmentsXlsx(List<AppointmentDto> appointments) {
        List<String> headers = List.of(
                "Date & heure", "Patient", "Médecin", "Service", "Type", "Motif", "Statut");
        List<List<Object>> rows = new ArrayList<>();
        for (AppointmentDto a : appointments) {
            rows.add(Arrays.asList(
                    fmt(a.getStartTime()),
                    a.getPatientName(),
                    a.getDoctorName(),
                    a.getDepartmentName(),
                    a.getType(),
                    a.getReason(),
                    a.getStatus()));
        }
        return excel.toXlsx("Rendez-vous", headers, rows);
    }

    // ── Journal des dispensations (sorties de stock) — une ligne par article ───────────
    public byte[] dispensationsXlsx(List<DispensationDto> dispensations) {
        List<String> headers = List.of(
                "Date", "N° ordonnance", "Patient", "N° dossier", "Médicament",
                "Lot", "Quantité", "Prix unitaire", "Total ligne", "Pharmacien");
        List<List<Object>> rows = new ArrayList<>();
        for (DispensationDto d : dispensations) {
            if (d.getItems() == null || d.getItems().isEmpty()) {
                rows.add(Arrays.asList(
                        fmt(d.getDispensedAt()), d.getPrescriptionNumber(), d.getPatientName(),
                        d.getPatientRecordNumber(), "", "", null, null, d.getTotalAmount(),
                        d.getPharmacistName()));
                continue;
            }
            for (DispensationItemDto it : d.getItems()) {
                rows.add(Arrays.asList(
                        fmt(d.getDispensedAt()),
                        d.getPrescriptionNumber(),
                        d.getPatientName(),
                        d.getPatientRecordNumber(),
                        it.getDrugName(),
                        it.getBatchNumber(),
                        it.getQuantity(),
                        it.getUnitPrice(),
                        it.getTotalPrice(),
                        d.getPharmacistName()));
            }
        }
        return excel.toXlsx("Dispensations", headers, rows);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────
    private static String fmt(LocalDate d) {
        return d != null ? d.format(DATE) : "";
    }

    private static String fmt(LocalDateTime d) {
        return d != null ? d.format(DATETIME) : "";
    }
}
