package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class PrescriptionItemDto {
    private Long id;
    private Long drugId;
    private String drugName;
    private String dosage;
    private String frequency;
    private String duration;
    private Integer quantity;
    private String instructions;
    private int sortOrder;
}
