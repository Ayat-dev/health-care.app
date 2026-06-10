package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter
public class RadiologyRequestDto {

    private Long id;
    private String requestNumber;
    private Long consultationId;
    private Long patientId;
    private Long doctorId;
    private String priority = "NORMAL";
    private String status;
    private String clinicalInfo;
    private LocalDateTime requestedAt;

    // Libellés pour l'affichage (mappés en lecture)
    private String patientName;
    private String patientRecordNumber;
    private String doctorName;

    /** Used by the creation form: the catalogue exams selected (checkboxes). */
    private List<Long> examIds = new ArrayList<>();

    /** The exam lines (display). */
    private List<RadiologyRequestItemDto> items = new ArrayList<>();

    /** Attached images (display). */
    private List<RadiologyImageDto> images = new ArrayList<>();

    // Compte-rendu (saisie + lecture)
    private Long reportId;
    private String findings;
    private String conclusion;
    private String radiologistName;
    private String validatedByName;
    private LocalDateTime validatedAt;

    // Indicateurs dérivés (lecture)
    private boolean hasReport;
    private int imageCount;
}
