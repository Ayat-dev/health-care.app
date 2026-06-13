package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Setter
public class PrenatalVisitDto {

    private Long id;
    private Long maternityRecordId;
    private Long doctorId;

    private LocalDate visitDate;
    private Integer visitNumber;
    private Integer gestationalAgeWeeks;
    private BigDecimal weightKg;
    private Integer bpSystolic;
    private Integer bpDiastolic;
    private Integer fetalHeartRate;
    private BigDecimal uterineHeightCm;
    private String presentation;
    private Boolean edema;
    private Boolean proteinuria;
    private Boolean ironSupplemented;
    private Boolean ttvVaccine;
    private String observations;
    private String recommendations;
    private LocalDate nextVisitDate;

    // Libellé (lecture)
    private String doctorName;
}
