package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter @Setter
public class RadiologyExamCatalogDto {

    private Long id;
    private String code;
    private String name;
    private String type;
    private String region;
    private BigDecimal price;
    private boolean active;
}
