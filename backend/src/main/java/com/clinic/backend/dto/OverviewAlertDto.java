package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Une alerte du « coup d'œil » patient (P3.6) : allergie, hospitalisation en cours,
 * impayés, résultats anormaux, grossesse à risque, tension élevée…
 */
@Getter @Setter
public class OverviewAlertDto {

    /** Gravité visuelle : RED, ORANGE, INFO. */
    private String level;
    private String icon;
    private String message;

    public OverviewAlertDto(String level, String icon, String message) {
        this.level = level;
        this.icon = icon;
        this.message = message;
    }
}
