package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter
public class LabRequestItemDto {

    private Long id;
    private Long testId;
    private String status;

    // Infos catalogue (lecture)
    private String testCode;
    private String testName;
    private String category;
    private String catalogReferenceRange;

    // Résultat (saisie + lecture)
    private Long resultId;
    private String resultValue;
    private String unit;
    private String referenceRange;
    private boolean abnormal;
    private String resultNotes;
    private String laborantinName;
    private String validatedByName;
    private LocalDateTime validatedAt;
}
