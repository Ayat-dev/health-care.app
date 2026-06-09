package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Carries insurance-provider data between the UI/API and {@code InsuranceProviderService}.
 */
@Getter @Setter
public class InsuranceProviderDto {
    private Long id;
    private String name;
    private String code;
    private String type;
    private BigDecimal coveragePercent;
    private String contact;
    private boolean active = true;
    private LocalDateTime createdAt;
}
