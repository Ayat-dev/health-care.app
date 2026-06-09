package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Carries act-catalog data between the UI/API and {@code ActCatalogService}.
 * {@code departmentId} is optional — an act can be clinic-wide.
 */
@Getter @Setter
public class ActCatalogDto {
    private Long id;
    private String code;
    private String name;
    private Long departmentId;
    private String departmentName; // read-only, for display in lists
    private BigDecimal price;
    private boolean active = true;
    private LocalDateTime createdAt;
}
