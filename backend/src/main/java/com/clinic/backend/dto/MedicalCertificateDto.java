package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Certificat médical (Tier E1) — lecture/écriture web.
 */
@Getter @Setter
public class MedicalCertificateDto {

    private Long id;
    private String certificateNumber;
    private String type;

    private Long patientId;
    private Long doctorId;
    private Long consultationId;

    private LocalDate issueDate;
    private LocalDate restStartDate;
    private LocalDate restEndDate;
    private Integer restDays;
    private String content;

    // Libellés (lecture)
    private String patientName;
    private String patientRecordNumber;
    private String doctorName;
    private LocalDateTime createdAt;
}
