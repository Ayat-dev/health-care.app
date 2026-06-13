package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** Tableau de bord du médecin connecté : sa journée et ce qui l'attend. */
@Getter @Setter
public class DoctorDashboardDto {

    private String doctorName;

    /** Mes consultations du jour (résumé léger). */
    private List<ConsultationDto> todayConsultations = new ArrayList<>();

    /** Mes résultats labo en attente de validation (status EN_COURS). */
    private long labPendingValidationCount;
    private List<LabRequestDto> labPendingValidation = new ArrayList<>();

    /** Mes rendez-vous de la semaine. */
    private List<AppointmentDto> weekAppointments = new ArrayList<>();
}
