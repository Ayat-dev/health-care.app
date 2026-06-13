package com.clinic.backend.maternity;

import com.clinic.backend.dto.MaternityAlertDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Derives "grossesse à risque" alerts from a dossier and its CPN visits (spec §"alertes automatiques").
 * Pure read-only computation — never throws, tolerant of nulls.
 */
@Component
public class MaternityRiskCalculator {

    private static final String RED = "RED";
    private static final String ORANGE = "ORANGE";

    /**
     * @param visits       the dossier's prenatal visits (any order)
     * @param gestAgeWeeks current gestational age in weeks (may be null)
     */
    public List<MaternityAlertDto> evaluate(List<PrenatalVisit> visits, Integer gestAgeWeeks) {
        List<MaternityAlertDto> alerts = new ArrayList<>();
        if (visits == null) visits = List.of();

        // Trier par date pour comparer la prise de poids entre visites consécutives.
        List<PrenatalVisit> sorted = new ArrayList<>(visits);
        sorted.sort(Comparator.comparing(PrenatalVisit::getVisitDate,
                Comparator.nullsLast(Comparator.naturalOrder())));

        // Tension artérielle systolique > 140 (sur n'importe quelle visite) → rouge.
        for (PrenatalVisit v : sorted) {
            if (v.getBpSystolic() != null && v.getBpSystolic() > 140) {
                alerts.add(new MaternityAlertDto(RED,
                        "Tension artérielle élevée (systolique " + v.getBpSystolic() + " mmHg) — risque de pré-éclampsie"));
                break;
            }
        }

        // Protéinurie positive → rouge.
        boolean proteinuria = sorted.stream().anyMatch(v -> Boolean.TRUE.equals(v.getProteinuria()));
        if (proteinuria) {
            alerts.add(new MaternityAlertDto(RED, "Protéinurie positive — risque de pré-éclampsie"));
        }

        // Prise de poids > 2 kg en ≤ 2 semaines entre deux visites consécutives → orange.
        for (int i = 1; i < sorted.size(); i++) {
            PrenatalVisit prev = sorted.get(i - 1);
            PrenatalVisit cur = sorted.get(i);
            if (prev.getWeightKg() == null || cur.getWeightKg() == null
                    || prev.getVisitDate() == null || cur.getVisitDate() == null) continue;
            long days = ChronoUnit.DAYS.between(prev.getVisitDate(), cur.getVisitDate());
            BigDecimal gain = cur.getWeightKg().subtract(prev.getWeightKg());
            if (days > 0 && days <= 14 && gain.compareTo(new BigDecimal("2")) > 0) {
                alerts.add(new MaternityAlertDto(ORANGE,
                        "Prise de poids rapide (+" + gain.stripTrailingZeros().toPlainString()
                                + " kg en " + days + " jours)"));
                break;
            }
        }

        // Moins de 4 CPN complétées à 36 semaines → vigilance.
        if (gestAgeWeeks != null && gestAgeWeeks >= 36 && sorted.size() < 4) {
            alerts.add(new MaternityAlertDto(ORANGE,
                    "Moins de 4 CPN à " + gestAgeWeeks + " SA (" + sorted.size() + " réalisée(s))"));
        }

        return alerts;
    }

    /** Gestational age in completed weeks from the LMP to a reference date (null-safe). */
    public Integer gestationalAgeWeeks(LocalDate lastPeriodDate, LocalDate reference) {
        if (lastPeriodDate == null || reference == null || reference.isBefore(lastPeriodDate)) return null;
        return (int) (ChronoUnit.DAYS.between(lastPeriodDate, reference) / 7);
    }
}
