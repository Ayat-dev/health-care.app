package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Generic "label + value(s)" row used by the reporting aggregates (top pathologies,
 * consultations per department, payments per method, age-group distribution, …).
 * Carries both a count and an optional money amount so a single shape covers both
 * count-based and money-based breakdowns.
 */
@Getter @Setter
public class LabelValueDto {

    private String label;
    private long count;
    private BigDecimal amount;

    public LabelValueDto() {
    }

    public LabelValueDto(String label, long count) {
        this.label = label;
        this.count = count;
    }

    public LabelValueDto(String label, BigDecimal amount) {
        this.label = label;
        this.amount = amount;
    }
}
