package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter
public class RadiologyImageDto {

    private Long id;
    private String url;
    private String caption;
    private LocalDateTime uploadedAt;

    /**
     * Data-URI base64 ({@code data:image/png;base64,…}) peuplé uniquement pour le
     * rendu PDF du bulletin (D4a) : openhtmltopdf ne peut pas suivre les URL
     * {@code /uploads/**} (chiffrées + derrière l'auth web) → l'image est embarquée.
     */
    private String dataUri;
}
