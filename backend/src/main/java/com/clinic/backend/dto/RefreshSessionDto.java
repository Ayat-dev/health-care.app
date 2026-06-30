package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * D1c — une session active (refresh token utilisable) d'un utilisateur, pour la vue admin
 * « sessions actives ». Un appareil = sa chaîne de rotation ; on n'expose JAMAIS le jeton
 * (ni brut ni haché), seulement son {@code id} (cible de la révocation) + ses métadonnées.
 */
@Getter @Setter
public class RefreshSessionDto {
    private Long id;
    private String userAgent;
    private String ipAddress;
    private LocalDateTime createdAt;
    private LocalDateTime lastUsedAt;
    private LocalDateTime expiresAt;
}
