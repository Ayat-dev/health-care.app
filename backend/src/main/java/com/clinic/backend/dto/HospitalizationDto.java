package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter
public class HospitalizationDto {

    private Long id;
    private Long patientId;
    private Long roomId;
    private Long doctorId;
    private LocalDateTime admissionDate;
    private LocalDateTime dischargeDate;
    private String admissionReason;
    private String diagnosisOnDischarge;
    private String status;
    private String notes;

    // Libellés pour l'affichage (mappés en lecture)
    private String patientName;
    private String patientRecordNumber;
    private String roomNumber;
    private String roomType;
    private String departmentName;
    private String doctorName;
    private BigDecimal dailyRate;

    // Indicateurs dérivés (lecture)
    private long daysSinceAdmission; // pour l'affichage "J+n" sur le plan des lits
    private long nights;             // nuits facturables (≥ 1)
    private BigDecimal estimatedCost;
}
