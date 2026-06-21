package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Une entrée de la recherche globale (P3.5) — palette de commandes.
 * Forme unique pour toutes les catégories (navigation, patients, factures…) :
 * un libellé principal, un sous-libellé optionnel, une URL cible et une icône.
 */
@Getter @Setter
public class SearchResultDto {

    /** Catégorie technique : NAV, PATIENT, INVOICE. */
    private String type;
    /** Libellé i18n de la catégorie (affiché en en-tête de groupe). */
    private String category;
    private String label;
    private String sublabel;
    private String url;
    private String icon;

    public SearchResultDto() {
    }

    public SearchResultDto(String type, String category, String label,
                           String sublabel, String url, String icon) {
        this.type = type;
        this.category = category;
        this.label = label;
        this.sublabel = sublabel;
        this.url = url;
        this.icon = icon;
    }
}
