package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Transporte un code CIM-10 entre l'UI/API et {@code Icd10Service}.
 */
@Getter @Setter
public class Icd10CodeDto {
    private Long id;
    private String code;
    private String title;
    private String category;
    private boolean active = true;
    private LocalDateTime createdAt;
}
