package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter
public class LabRequestDto {

    private Long id;
    private String requestNumber;
    private Long consultationId;
    private Long patientId;
    private Long doctorId;
    private String priority = "NORMAL";
    private String status;
    private String notes;
    private LocalDateTime requestedAt;

    // Libellés pour l'affichage (mappés en lecture)
    private String patientName;
    private String patientRecordNumber;
    private String doctorName;

    /** Used by the creation form: the catalogue analyses selected (checkboxes). */
    private List<Long> testIds = new ArrayList<>();

    /** The analysis lines (display + result entry). */
    private List<LabRequestItemDto> items = new ArrayList<>();

    // Indicateurs dérivés (lecture)
    private long abnormalCount;
    private boolean allResultsEntered;
}
