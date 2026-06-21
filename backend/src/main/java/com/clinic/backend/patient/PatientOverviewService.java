package com.clinic.backend.patient;

import com.clinic.backend.consultation.Consultation;
import com.clinic.backend.dto.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Construit la vue « coup d'œil » + timeline du dossier patient (P3.6).
 * <p>
 * <b>Pur, sans accès base</b> : opère uniquement sur les données déjà chargées
 * (et déjà mappées dans leur transaction) par {@code PatientWebController}.
 * Aucune requête supplémentaire, aucun accès lazy → sûr avec OSIV désactivé.
 */
@Service
public class PatientOverviewService {

    public PatientOverviewDto build(Patient patient,
                                    List<Consultation> consultations,
                                    List<LabRequestDto> labs,
                                    List<RadiologyRequestDto> radios,
                                    List<HospitalizationDto> stays,
                                    List<InvoiceDto> invoices,
                                    MaternityRecordDto maternity) {

        PatientOverviewDto o = new PatientOverviewDto();

        // ── Démographie / médical de base ────────────────────────────────────
        if (patient.getBirthDate() != null) {
            o.setAgeYears(Period.between(patient.getBirthDate(), LocalDate.now()).getYears());
        }
        o.setBloodType(patient.getBloodType());
        o.setAllergies(patient.getAllergies());
        o.setChronicConditions(patient.getChronicConditions());
        o.setHasAllergies(hasText(patient.getAllergies()));

        // ── Dernières constantes : consultation la plus récente avec mesures ──
        Consultation vit = consultations == null ? null : consultations.stream()
                .filter(PatientOverviewService::hasAnyVital)
                .max(Comparator.comparing(Consultation::getConsultationDate))
                .orElse(null);
        if (vit != null) {
            o.setHasVitals(true);
            o.setVitalsDate(vit.getConsultationDate());
            if (vit.getBpSystolic() != null && vit.getBpDiastolic() != null) {
                o.setBloodPressure(vit.getBpSystolic() + "/" + vit.getBpDiastolic());
            }
            o.setTemperatureC(vit.getTemperatureC());
            o.setPulseBpm(vit.getPulseBpm());
            o.setSpo2Percent(vit.getSpo2Percent());
            o.setWeightKg(vit.getWeightKg());
        }

        // ── Alertes (RED d'abord, puis ORANGE, puis INFO) ────────────────────
        List<OverviewAlertDto> a = o.getAlerts();

        if (o.isHasAllergies()) {
            a.add(new OverviewAlertDto("RED", "⚠️", "Allergies : " + patient.getAllergies().trim()));
        }
        if (maternity != null && maternity.getAlerts() != null && !maternity.getAlerts().isEmpty()) {
            a.add(new OverviewAlertDto("RED", "🤰",
                    "Grossesse à risque — " + maternity.getAlerts().size() + " alerte(s)"));
        }
        if (stays != null) {
            stays.stream().filter(h -> "ADMIS".equals(h.getStatus())).findFirst()
                    .ifPresent(h -> a.add(new OverviewAlertDto("ORANGE", "🏥",
                            "Actuellement hospitalisé — Chambre " + h.getRoomNumber())));
        }
        if (vit != null && ((vit.getBpSystolic() != null && vit.getBpSystolic() > 140)
                || (vit.getBpDiastolic() != null && vit.getBpDiastolic() > 90))) {
            a.add(new OverviewAlertDto("ORANGE", "🫀", "Tension élevée — " + o.getBloodPressure() + " mmHg"));
        }
        if (labs != null) {
            long abnormal = labs.stream().mapToLong(LabRequestDto::getAbnormalCount).sum();
            if (abnormal > 0) {
                a.add(new OverviewAlertDto("ORANGE", "🔬",
                        "Résultats de laboratoire anormaux (" + abnormal + ")"));
            }
        }
        if (invoices != null) {
            List<InvoiceDto> unpaid = invoices.stream()
                    .filter(i -> "EN_ATTENTE".equals(i.getStatus()) || "PARTIEL".equals(i.getStatus()))
                    .toList();
            if (!unpaid.isEmpty()) {
                BigDecimal due = unpaid.stream().map(InvoiceDto::getBalanceDue)
                        .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
                a.add(new OverviewAlertDto("ORANGE", "💳",
                        unpaid.size() + " facture(s) impayée(s) — reste " + money(due) + " F"));
            }
        }
        if (hasText(patient.getChronicConditions())) {
            a.add(new OverviewAlertDto("INFO", "🩺",
                    "Antécédents : " + patient.getChronicConditions().trim()));
        }

        // ── Timeline unifiée (plus récent en premier) ────────────────────────
        List<TimelineEventDto> tl = o.getTimeline();
        if (consultations != null) {
            for (Consultation c : consultations) {
                tl.add(new TimelineEventDto(c.getConsultationDate(), "🩺", "Consultation",
                        hasText(c.getChiefComplaint()) ? c.getChiefComplaint() : "Consultation",
                        consultationSubtitle(c), c.getStatus(), "/consultations/" + c.getId()));
            }
        }
        if (labs != null) {
            for (LabRequestDto r : labs) {
                String sub = (r.getItems() == null ? 0 : r.getItems().size()) + " analyse(s)"
                        + (r.getAbnormalCount() > 0 ? " · " + r.getAbnormalCount() + " anormal" : "");
                tl.add(new TimelineEventDto(r.getRequestedAt(), "🔬", "Laboratoire",
                        r.getRequestNumber(), sub, r.getStatus(), "/lab/requests/" + r.getId()));
            }
        }
        if (radios != null) {
            for (RadiologyRequestDto r : radios) {
                String sub = (r.getItems() == null ? 0 : r.getItems().size()) + " examen(s)";
                tl.add(new TimelineEventDto(r.getRequestedAt(), "🩻", "Imagerie",
                        r.getRequestNumber(), sub, r.getStatus(), "/radiology/requests/" + r.getId()));
            }
        }
        if (stays != null) {
            for (HospitalizationDto h : stays) {
                String sub = (h.getDepartmentName() != null ? h.getDepartmentName() + " · " : "")
                        + h.getNights() + " nuit(s)";
                tl.add(new TimelineEventDto(h.getAdmissionDate(), "🏥", "Hospitalisation",
                        "Chambre " + h.getRoomNumber(), sub, h.getStatus(), "/hospitalization/" + h.getId()));
            }
        }
        if (invoices != null) {
            for (InvoiceDto i : invoices) {
                tl.add(new TimelineEventDto(i.getCreatedAt(), "💳", "Facturation",
                        i.getInvoiceNumber(), "Part patient " + money(i.getPatientAmount()) + " F",
                        i.getStatus(), "/billing/invoices/" + i.getId()));
            }
        }
        tl.removeIf(e -> e.getDateTime() == null);
        tl.sort(Comparator.comparing(TimelineEventDto::getDateTime).reversed());

        return o;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static boolean hasAnyVital(Consultation c) {
        return c.getWeightKg() != null || c.getTemperatureC() != null
                || c.getBpSystolic() != null || c.getBpDiastolic() != null
                || c.getPulseBpm() != null || c.getSpo2Percent() != null;
    }

    private static String consultationSubtitle(Consultation c) {
        String doctor = c.getDoctor() != null ? c.getDoctor().getFullName() : null;
        String diag = hasText(c.getDiagnosis()) ? c.getDiagnosis() : null;
        if (doctor != null && diag != null) return doctor + " · " + diag;
        if (doctor != null) return doctor;
        return diag != null ? diag : "";
    }

    private static String money(BigDecimal v) {
        if (v == null) return "0";
        return v.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }
}
