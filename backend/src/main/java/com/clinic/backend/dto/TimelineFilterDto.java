package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Une puce de filtre de la timeline patient (Z5) : une catégorie présente dans
 * le dossier, avec son libellé i18n, sa clé stable (pour le filtrage client) et
 * le nombre d'évènements de cette catégorie.
 */
@Getter @Setter
public class TimelineFilterDto {

    private String key;    // consultation, lab, imaging, hospitalization, billing, maternity
    private String label;  // libellé i18n
    private long count;

    public TimelineFilterDto(String key, String label, long count) {
        this.key = key;
        this.label = label;
        this.count = count;
    }
}
