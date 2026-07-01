package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

/** Une règle d'interaction médicamenteuse (E2-B), passée au vérificateur. */
@Getter @Setter
public class DrugInteractionDto {

    private Long id;
    private String dciA;
    private String dciB;
    private String severity;      // MINEURE / MODEREE / MAJEURE
    private String description;

    public DrugInteractionDto() { }

    public DrugInteractionDto(String dciA, String dciB, String severity, String description) {
        this.dciA = dciA;
        this.dciB = dciB;
        this.severity = severity;
        this.description = description;
    }
}
