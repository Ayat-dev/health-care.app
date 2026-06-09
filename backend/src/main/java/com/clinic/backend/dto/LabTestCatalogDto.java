package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Carries lab-test-catalog data between the UI/API and {@code LabTestCatalogService}.
 */
@Getter @Setter
public class LabTestCatalogDto {
    private Long id;
    private String code;
    private String name;
    private String category;
    private BigDecimal price;
    private Integer turnaroundHours;
    private String referenceRange;
    private boolean active = true;
    private LocalDateTime createdAt;
}
