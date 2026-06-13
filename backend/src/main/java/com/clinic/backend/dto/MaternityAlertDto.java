package com.clinic.backend.dto;

import lombok.Getter;

/** A risk alert raised on a maternity dossier (grossesse à risque). */
@Getter
public class MaternityAlertDto {

    /** RED (danger), ORANGE (vigilance). */
    private final String level;
    private final String message;

    public MaternityAlertDto(String level, String message) {
        this.level = level;
        this.message = message;
    }
}
