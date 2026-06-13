package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter
public class MaternityRecordDto {

    private Long id;
    private Long patientId;
    private Long doctorId;

    private Integer gravidity;
    private Integer parity;
    private LocalDate lastPeriodDate;
    private LocalDate expectedDueDate;

    // Accouchement
    private LocalDate deliveryDate;
    private String deliveryType;     // NATUREL, CESARIENNE, FORCEPS
    private String deliveryOutcome;  // VIVANT, MORT_NE, AVORTEMENT
    private Integer newbornWeightG;
    private Integer newbornApgar1;
    private Integer newbornApgar5;
    private String newbornGender;
    private String complications;

    private String notes;
    private String status;

    // Libellés pour l'affichage (mappés en lecture)
    private String patientName;
    private String patientRecordNumber;
    private String doctorName;

    // Indicateurs dérivés (lecture)
    private Integer currentGestationalAgeWeeks; // âge gestationnel aujourd'hui (si non accouchée)
    private int completedVisits;

    private List<PrenatalVisitDto> visits = new ArrayList<>();
    private List<MaternityAlertDto> alerts = new ArrayList<>();
}
