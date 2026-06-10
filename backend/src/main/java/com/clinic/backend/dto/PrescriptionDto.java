package com.clinic.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter
public class PrescriptionDto {
    private Long id;
    private String prescriptionNumber;
    private Long consultationId;
    private Long patientId;
    private Long doctorId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate issueDate;

    private int validityDays = 30;
    private String notes;
    private boolean dispensed;

    private List<PrescriptionItemDto> items = new ArrayList<>();

    // Read-only display fields (populated when mapping entity → DTO)
    private String patientName;
    private String patientRecordNumber;
    private String doctorName;
}
