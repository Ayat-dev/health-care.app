package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class RadiologyRequestItemDto {

    private Long id;
    private Long examId;

    // Infos catalogue (lecture)
    private String examCode;
    private String examName;
    private String examType;
    private String examRegion;
}
