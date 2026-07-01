package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Avertissement d'interaction (E2-B) : deux médicaments dispensés se recoupent avec une
 * interaction connue. <b>Non-bloquant</b> — informe le pharmacien, ne refuse jamais.
 */
@Getter @Setter
public class InteractionWarningDto {

    private String drugA;
    private String drugB;
    private String severity;      // MINEURE / MODEREE / MAJEURE
    private String description;

    public InteractionWarningDto(String drugA, String drugB, String severity, String description) {
        this.drugA = drugA;
        this.drugB = drugB;
        this.severity = severity;
        this.description = description;
    }
}
