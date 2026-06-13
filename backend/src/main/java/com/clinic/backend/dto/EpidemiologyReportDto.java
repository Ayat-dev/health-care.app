package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** Statistiques épidémiologiques : pathologies fréquentes + prévalence par âge et sexe. */
@Getter @Setter
public class EpidemiologyReportDto {

    private int month;
    private int year;
    private long totalConsultations;

    /** Top pathologies (diagnostics) du mois. */
    private List<LabelValueDto> topPathologies = new ArrayList<>();

    /** Répartition des consultations par tranche d'âge (0-4, 5-14, 15-44, 45-64, 65+). */
    private List<LabelValueDto> byAgeGroup = new ArrayList<>();

    /** Répartition par sexe. */
    private List<LabelValueDto> bySex = new ArrayList<>();

    /** Consultations par département. */
    private List<LabelValueDto> byDepartment = new ArrayList<>();
}
