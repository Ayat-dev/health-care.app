package com.clinic.backend.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter
public class AppointmentDto {
    private Long id;
    private Long patientId;
    private Long doctorId;
    private Long departmentId;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime endTime;

    private String status;
    private String type;
    private String reason;
    private String notes;

    // Read-only display fields (populated when mapping entity → DTO)
    private String patientName;
    private String doctorName;
    private String departmentName;
}
