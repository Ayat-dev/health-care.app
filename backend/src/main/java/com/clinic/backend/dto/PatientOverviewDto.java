package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Vue « coup d'œil » du patient (P3.6) : synthèse en tête de dossier
 * (allergies, antécédents, dernières constantes, alertes) + timeline unifiée.
 * Construit en mémoire à partir des données déjà chargées par le contrôleur
 * (aucune requête supplémentaire, aucun accès lazy).
 */
@Getter @Setter
public class PatientOverviewDto {

    private Integer ageYears;
    private String bloodType;
    private String allergies;
    private String chronicConditions;
    private boolean hasAllergies;

    // ── Dernières constantes (consultation la plus récente avec mesures) ──────
    private boolean hasVitals;
    private LocalDateTime vitalsDate;
    private String bloodPressure;   // "120/80"
    private BigDecimal temperatureC;
    private Integer pulseBpm;
    private BigDecimal spo2Percent;
    private BigDecimal weightKg;

    private List<OverviewAlertDto> alerts = new ArrayList<>();
    private List<TimelineEventDto> timeline = new ArrayList<>();
}
