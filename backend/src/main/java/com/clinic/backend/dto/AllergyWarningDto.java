package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Avertissement d'allergie (E2-A) : un médicament dispensé recoupe une allergie connue
 * du patient. <b>Non-bloquant</b> — informe le pharmacien, ne refuse jamais la dispensation.
 */
@Getter @Setter
public class AllergyWarningDto {

    private String drugName;   // médicament concerné
    private String allergen;   // terme d'allergie recoupé (classe / DCI / nom)

    public AllergyWarningDto(String drugName, String allergen) {
        this.drugName = drugName;
        this.allergen = allergen;
    }
}
