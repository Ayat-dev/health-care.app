package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter
public class RadiologyImageDto {

    private Long id;
    private String url;
    private String caption;
    private LocalDateTime uploadedAt;
}
