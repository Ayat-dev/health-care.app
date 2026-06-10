package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter
public class DrugDto {
    private Long id;
    private String code;
    private String name;
    private String genericName;
    private String category;
    private String form;
    private String dosageStrength;
    private String unit;
    private boolean requiresPrescription = true;
    private boolean active = true;
    private String notes;
    private LocalDateTime createdAt;
}
