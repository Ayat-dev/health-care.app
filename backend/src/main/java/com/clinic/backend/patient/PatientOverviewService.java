package com.clinic.backend.patient;

import com.clinic.backend.consultation.Consultation;
import com.clinic.backend.dto.*;
import com.clinic.backend.i18n.WebI18n;
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

    private final WebI18n i18n;

    public PatientOverviewService(WebI18n i18n) {
        this.i18n = i18n;
    }

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
            a.add(new OverviewAlertDto("RED", "⚠️",
                    i18n.t("patients.overview.alert_allergies", patient.getAllergies().trim())));
        }
        if (maternity != null && maternity.getAlerts() != null && !maternity.getAlerts().isEmpty()) {
            a.add(new OverviewAlertDto("RED", "🤰",
                    i18n.t("patients.overview.alert_maternity", maternity.getAlerts().size())));
        }
        if (stays != null) {
            stays.stream().filter(h -> "ADMIS".equals(h.getStatus())).findFirst()
                    .ifPresent(h -> a.add(new OverviewAlertDto("ORANGE", "🏥",
                            i18n.t("patients.overview.alert_hospitalized", h.getRoomNumber()))));
        }
        if (vit != null && ((vit.getBpSystolic() != null && vit.getBpSystolic() > 140)
                || (vit.getBpDiastolic() != null && vit.getBpDiastolic() > 90))) {
            a.add(new OverviewAlertDto("ORANGE", "🫀",
                    i18n.t("patients.overview.alert_bp_high", o.getBloodPressure())));
        }
        if (labs != null) {
            long abnormal = labs.stream().mapToLong(LabRequestDto::getAbnormalCount).sum();
            if (abnormal > 0) {
                a.add(new OverviewAlertDto("ORANGE", "🔬",
                        i18n.t("patients.overview.alert_lab_abnormal", abnormal)));
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
                        i18n.t("patients.overview.alert_unpaid", unpaid.size(), money(due))));
            }
        }
        if (hasText(patient.getChronicConditions())) {
            a.add(new OverviewAlertDto("INFO", "🩺",
                    i18n.t("patients.overview.alert_chronic", patient.getChronicConditions().trim())));
        }

        // ── Timeline unifiée (plus récent en premier) ────────────────────────
        List<TimelineEventDto> tl = o.getTimeline();
        if (consultations != null) {
            for (Consultation c : consultations) {
                tl.add(new TimelineEventDto(c.getConsultationDate(), "🩺",
                        i18n.t("patients.overview.cat_consultation"), "consultation",
                        hasText(c.getChiefComplaint()) ? c.getChiefComplaint()
                                : i18n.t("patients.overview.tl_consultation_default"),
                        consultationSubtitle(c), c.getStatus(), "/consultations/" + c.getId()));
            }
        }
        if (labs != null) {
            for (LabRequestDto r : labs) {
                String sub = i18n.t("patients.overview.tl_analyses", r.getItems() == null ? 0 : r.getItems().size())
                        + (r.getAbnormalCount() > 0
                            ? i18n.t("patients.overview.tl_abnormal_suffix", r.getAbnormalCount()) : "");
                tl.add(new TimelineEventDto(r.getRequestedAt(), "🔬", i18n.t("patients.overview.cat_lab"), "lab",
                        r.getRequestNumber(), sub, r.getStatus(), "/lab/requests/" + r.getId()));
            }
        }
        if (radios != null) {
            for (RadiologyRequestDto r : radios) {
                String sub = i18n.t("patients.overview.tl_exams", r.getItems() == null ? 0 : r.getItems().size());
                tl.add(new TimelineEventDto(r.getRequestedAt(), "🩻", i18n.t("patients.overview.cat_imaging"), "imaging",
                        r.getRequestNumber(), sub, r.getStatus(), "/radiology/requests/" + r.getId()));
            }
        }
        if (stays != null) {
            for (HospitalizationDto h : stays) {
                String sub = (h.getDepartmentName() != null ? h.getDepartmentName() + " · " : "")
                        + i18n.t("patients.overview.tl_nights", h.getNights());
                tl.add(new TimelineEventDto(h.getAdmissionDate(), "🏥",
                        i18n.t("patients.overview.cat_hospitalization"), "hospitalization",
                        i18n.t("patients.overview.tl_room", h.getRoomNumber()), sub, h.getStatus(),
                        "/hospitalization/" + h.getId()));
            }
        }
        if (invoices != null) {
            for (InvoiceDto i : invoices) {
                tl.add(new TimelineEventDto(i.getCreatedAt(), "💳", i18n.t("patients.overview.cat_billing"), "billing",
                        i.getInvoiceNumber(),
                        i18n.t("patients.overview.tl_patient_part", money(i.getPatientAmount())),
                        i.getStatus(), "/billing/invoices/" + i.getId()));
            }
        }
        // Maternité : chaque CPN (visite prénatale) + l'accouchement deviennent des évènements.
        if (maternity != null) {
            String url = "/maternity/" + maternity.getId();
            if (maternity.getVisits() != null) {
                for (PrenatalVisitDto v : maternity.getVisits()) {
                    if (v.getVisitDate() == null) continue;
                    tl.add(new TimelineEventDto(v.getVisitDate().atStartOfDay(), "🤰",
                            i18n.t("patients.overview.cat_maternity"), "maternity",
                            i18n.t("patients.overview.tl_cpn", v.getVisitNumber() == null ? "?" : v.getVisitNumber()),
                            cpnSubtitle(v), null, url));
                }
            }
            if (maternity.getDeliveryDate() != null) {
                tl.add(new TimelineEventDto(maternity.getDeliveryDate().atStartOfDay(), "👶",
                        i18n.t("patients.overview.cat_maternity"), "maternity",
                        i18n.t("patients.overview.tl_delivery"), deliverySubtitle(maternity),
                        maternity.getStatus(), url));
            }
        }
        tl.removeIf(e -> e.getDateTime() == null);
        tl.sort(Comparator.comparing(TimelineEventDto::getDateTime).reversed());

        // Puces de filtre : catégories présentes (ordre stable), avec compteurs.
        o.setTimelineFilters(buildFilters(tl));

        // Tendances des constantes (sparklines) à partir des consultations.
        o.setSparklines(buildSparklines(consultations));

        return o;
    }

    /** Catégories présentes dans la timeline, ordre canonique, avec le nombre d'évènements. */
    private List<TimelineFilterDto> buildFilters(List<TimelineEventDto> tl) {
        java.util.Map<String, Long> counts = tl.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        TimelineEventDto::getCategoryKey, java.util.stream.Collectors.counting()));
        // Clés de filtre et suffixes i18n alignés (cat_consultation, cat_lab, cat_imaging…).
        String[] order = {"consultation", "lab", "imaging", "hospitalization", "billing", "maternity"};
        List<TimelineFilterDto> filters = new java.util.ArrayList<>();
        for (String key : order) {
            Long n = counts.get(key);
            if (n != null && n > 0) {
                filters.add(new TimelineFilterDto(key, i18n.t("patients.overview.cat_" + key), n));
            }
        }
        return filters;
    }

    /** Sous-titre d'une CPN : âge gestationnel + tension (si présents). */
    private String cpnSubtitle(PrenatalVisitDto v) {
        List<String> parts = new java.util.ArrayList<>();
        if (v.getGestationalAgeWeeks() != null) {
            parts.add(i18n.t("patients.overview.tl_sa", v.getGestationalAgeWeeks()));
        }
        if (v.getBpSystolic() != null && v.getBpDiastolic() != null) {
            parts.add(v.getBpSystolic() + "/" + v.getBpDiastolic() + " mmHg");
        }
        return String.join(" · ", parts);
    }

    /** Sous-titre de l'accouchement : type + poids du nouveau-né (si présents). */
    private static String deliverySubtitle(MaternityRecordDto m) {
        List<String> parts = new java.util.ArrayList<>();
        if (hasText(m.getDeliveryType())) parts.add(m.getDeliveryType());
        if (m.getNewbornWeightG() != null) parts.add(m.getNewbornWeightG() + " g");
        return String.join(" · ", parts);
    }

    /**
     * Sparklines des constantes : une courbe par mesure disposant d'au moins 2 points.
     * Les coordonnées SVG sont normalisées dans un viewBox 120×32 (pad 3), au format
     * <b>Locale.US</b> (séparateur décimal « . » — sinon la virgule française casserait
     * l'attribut {@code points} du {@code <polyline>}).
     */
    private List<VitalsSparklineDto> buildSparklines(List<Consultation> consultations) {
        List<VitalsSparklineDto> out = new java.util.ArrayList<>();
        if (consultations == null) return out;
        List<Consultation> sorted = consultations.stream()
                .filter(c -> c.getConsultationDate() != null)
                .sorted(Comparator.comparing(Consultation::getConsultationDate))
                .toList();
        addSpark(out, sorted, "weight", i18n.t("patients.overview.spark_weight"), "kg",
                c -> c.getWeightKg() == null ? null : c.getWeightKg().doubleValue(), 1);
        addSpark(out, sorted, "bp_sys", i18n.t("patients.overview.spark_bp"), "mmHg",
                c -> c.getBpSystolic() == null ? null : c.getBpSystolic().doubleValue(), 0);
        addSpark(out, sorted, "pulse", i18n.t("patients.overview.spark_pulse"), "bpm",
                c -> c.getPulseBpm() == null ? null : c.getPulseBpm().doubleValue(), 0);
        addSpark(out, sorted, "temp", i18n.t("patients.overview.spark_temp"), "°C",
                c -> c.getTemperatureC() == null ? null : c.getTemperatureC().doubleValue(), 1);
        return out;
    }

    private void addSpark(List<VitalsSparklineDto> out, List<Consultation> sorted, String key,
                          String label, String unit,
                          java.util.function.Function<Consultation, Double> extractor, int decimals) {
        List<Double> vals = new java.util.ArrayList<>();
        for (Consultation c : sorted) {
            Double v = extractor.apply(c);
            if (v != null) vals.add(v);
        }
        int n = vals.size();
        if (n < 2) return;
        double min = vals.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = vals.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double span = max - min;
        final double W = 120, H = 32, pad = 3;
        StringBuilder pts = new StringBuilder();
        for (int i = 0; i < n; i++) {
            double x = pad + i * (W - 2 * pad) / (n - 1);
            double y = span == 0 ? H / 2 : (H - pad) - (vals.get(i) - min) / span * (H - 2 * pad);
            if (i > 0) pts.append(' ');
            pts.append(String.format(java.util.Locale.US, "%.1f", x))
               .append(',').append(String.format(java.util.Locale.US, "%.1f", y));
        }
        out.add(new VitalsSparklineDto(key, label, unit, pts.toString(),
                fmt(vals.get(n - 1), decimals), fmt(min, decimals), fmt(max, decimals), n));
    }

    private static String fmt(double v, int decimals) {
        return BigDecimal.valueOf(v).setScale(decimals, RoundingMode.HALF_UP).toPlainString();
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
