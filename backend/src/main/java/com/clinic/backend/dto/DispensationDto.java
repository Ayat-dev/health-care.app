package com.clinic.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter
public class DispensationDto {
    private Long id;
    private Long prescriptionId;   // null = vente libre
    private Long patientId;
    private String notes;

    private List<DispensationItemDto> items = new ArrayList<>();

    // Read-only display fields (populated when mapping entity → DTO)
    private String prescriptionNumber;
    private String patientName;
    private String patientRecordNumber;
    private String pharmacistName;
    private BigDecimal totalAmount;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime dispensedAt;
}
