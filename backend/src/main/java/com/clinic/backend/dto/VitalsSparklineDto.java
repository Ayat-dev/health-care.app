package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Un mini-graphique de tendance d'une constante vitale (Z5) — « sparkline ».
 * <p>
 * Construit en mémoire à partir des consultations déjà chargées : la chaîne
 * {@code points} est directement injectable dans un {@code <polyline>} SVG
 * (viewBox normalisé côté service), sans calcul côté template.
 */
@Getter @Setter
public class VitalsSparklineDto {

    private String key;        // weight, bp_sys, pulse, temp
    private String label;      // libellé i18n
    private String unit;       // kg, mmHg, bpm, °C
    private String points;     // "x,y x,y …" pour <polyline> (viewBox 0 0 120 32)
    private String lastValue;  // dernière mesure (formatée)
    private String minValue;
    private String maxValue;
    private int count;         // nombre de mesures

    public VitalsSparklineDto(String key, String label, String unit, String points,
                              String lastValue, String minValue, String maxValue, int count) {
        this.key = key;
        this.label = label;
        this.unit = unit;
        this.points = points;
        this.lastValue = lastValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.count = count;
    }
}
