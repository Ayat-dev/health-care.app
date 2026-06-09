package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Carries department data between the UI/API and {@code DepartmentService}.
 */
@Getter @Setter
public class DepartmentDto {
    private Long id;
    private String code;
    private String name;
    private String description;
    private String color;
    private boolean active = true;
    private LocalDateTime createdAt;
}
