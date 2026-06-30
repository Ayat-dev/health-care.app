package com.clinic.backend.controller.web;

import com.clinic.backend.storage.FileStorageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Sert les fichiers uploadés ({@code /uploads/**}) en les <b>déchiffrant à la volée</b>
 * (D3b) : le contenu est chiffré sur disque (AES-GCM), donc il ne peut plus être servi
 * tel quel par un handler de ressources statiques — ce contrôleur les passe par
 * {@link FileStorageService#load} qui déchiffre.
 *
 * <p>{@code Cache-Control: no-store} — ces fichiers sont du PHI (photos/imageries) :
 * jamais mis en cache disque par le navigateur ou le service worker.
 */
@Controller
@RequiredArgsConstructor
public class UploadedFileController {

    private final FileStorageService fileStorageService;

    @GetMapping("/uploads/**")
    public ResponseEntity<byte[]> serve(HttpServletRequest request) {
        String uri = URLDecoder.decode(request.getRequestURI(), StandardCharsets.UTF_8);
        // Garde-fou : on ne sert que sous /uploads/ ; load() revalide l'absence de traversée.
        if (!uri.startsWith("/uploads/")) {
            return ResponseEntity.notFound().build();
        }
        FileStorageService.StoredFile file = fileStorageService.load(uri);
        if (file == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .cacheControl(CacheControl.noStore())
                .body(file.content());
    }
}
