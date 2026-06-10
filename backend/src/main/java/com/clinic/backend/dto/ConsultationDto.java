package com.clinic.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter
public class ConsultationDto {
    private Long id;
    private Long appointmentId;
    private Long patientId;
    private Long doctorId;
    private Long departmentId;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime consultationDate;

    // Constantes vitales
    private BigDecimal weightKg;
    private BigDecimal heightCm;
    private BigDecimal temperatureC;
    private Integer bpSystolic;
    private Integer bpDiastolic;
    private Integer pulseBpm;
    private BigDecimal spo2Percent;
    private Integer respiratoryRate;

    // Clinique
    private String chiefComplaint;
    private String history;
    private String physicalExam;
    private String diagnosis;
    private String icd10Codes;
    private String treatmentPlan;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate followUpDate;

    private boolean emergency;
    private String status;

    // Read-only display fields (populated when mapping entity → DTO)
    private String patientName;
    private String patientRecordNumber;
    private String doctorName;
    private String departmentName;
}
