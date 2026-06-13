package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** Rapport d'activité médicale mensuel : volumes par module + répartition par service. */
@Getter @Setter
public class ActivityReportDto {

    private int month;
    private int year;

    private long consultations;
    private long appointments;
    private long newPatients;
    private long labRequests;
    private long admissions;

    /** Consultations par département (service) sur le mois. */
    private List<LabelValueDto> consultationsByDepartment = new ArrayList<>();
}
