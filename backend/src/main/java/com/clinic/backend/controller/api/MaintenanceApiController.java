package com.clinic.backend.controller.api;

import com.clinic.backend.storage.FileEncryptionService;
import com.clinic.backend.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Routes de maintenance/ops (D3b). Réservées aux administrateurs.
 */
@RestController
@RequestMapping("/api/admin/maintenance")
@RequiredArgsConstructor
public class MaintenanceApiController {

    private final FileStorageService fileStorageService;
    private final FileEncryptionService fileEncryptionService;

    /**
     * Rotation de la clé de chiffrement des fichiers (D3b) : re-chiffre tous les
     * fichiers du répertoire de stockage avec la clé courante (en relisant via la clé
     * courante, l'ancienne {@code app.storage.encryption.previous-key}, ou en clair pour
     * les fichiers legacy). À lancer après avoir promu une nouvelle clé ; l'ancienne clé
     * peut ensuite être retirée de la configuration.
     */
    @PostMapping("/rotate-file-encryption")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public Map<String, Object> rotateFileEncryption() {
        int rotated = fileEncryptionService.rotateAll(fileStorageService.getRoot());
        return Map.of("rotated", rotated);
    }
}
