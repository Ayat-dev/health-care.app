package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Un évènement de la timeline patient unifiée (P3.6) — une ligne chronologique
 * agrégeant consultations, analyses, imageries, séjours et factures.
 */
@Getter @Setter
public class TimelineEventDto {

    private LocalDateTime dateTime;
    private String icon;
    private String category;    // libellé i18n (Consultation, Laboratoire…)
    private String categoryKey; // clé stable pour le filtrage client (consultation, lab, imaging…)
    private String title;
    private String subtitle;
    private String status;      // pour le badge (peut être null)
    private String url;

    public TimelineEventDto(LocalDateTime dateTime, String icon, String category, String categoryKey,
                            String title, String subtitle, String status, String url) {
        this.dateTime = dateTime;
        this.icon = icon;
        this.category = category;
        this.categoryKey = categoryKey;
        this.title = title;
        this.subtitle = subtitle;
        this.status = status;
        this.url = url;
    }
}
