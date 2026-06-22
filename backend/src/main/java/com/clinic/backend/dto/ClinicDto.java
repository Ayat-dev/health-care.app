package com.clinic.backend.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor
public class ClinicDto {

    private Long id;
    private String code;
    private String name;
    private String address;
    private String phone;
    private String email;
    private boolean active = true;
    private LocalDateTime createdAt;
}
