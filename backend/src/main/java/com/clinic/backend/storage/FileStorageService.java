package com.clinic.backend.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

/**
 * Stockage de fichiers sur disque, exposés ensuite sous l'URL publique {@code /uploads/**}
 * (voir {@link com.clinic.backend.config.WebConfig}).
 */
@Service
@Slf4j
public class FileStorageService {

    private static final Set<String> ALLOWED_IMAGE_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");

    private final Path root;

    public FileStorageService(@Value("${app.storage.upload-dir:uploads}") String uploadDir) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    /**
     * Enregistre une image dans le sous-dossier {@code subdir} et retourne son chemin web
     * public (ex. {@code /uploads/patients/42/<uuid>.jpg}).
     *
     * @throws IllegalArgumentException si le fichier est vide ou d'un format non supporté
     */
    public String storeImage(MultipartFile file, String subdir) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Aucun fichier sélectionné.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Format non supporté : JPEG, PNG ou WebP attendu.");
        }

        String ext = switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
        String filename = UUID.randomUUID() + ext;

        try {
            Path dir = root.resolve(subdir).normalize();
            if (!dir.startsWith(root)) {
                throw new IllegalArgumentException("Chemin de destination invalide.");
            }
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            file.transferTo(target);
            log.info("Fichier stocké : {}", target);
            return "/uploads/" + subdir + "/" + filename;
        } catch (IOException e) {
            throw new UncheckedIOException("Échec de l'enregistrement du fichier.", e);
        }
    }

    /** Contenu d'un fichier chargé depuis le disque + son type MIME. */
    public record StoredFile(byte[] content, String contentType) {}

    /**
     * Charge le fichier désigné par son chemin web public ({@code /uploads/...}).
     * Utilisé par l'API REST (chaîne JWT) pour servir des fichiers normalement
     * exposés sous {@code /uploads/**} (chaîne web/session), inaccessibles au client
     * lourd. Renvoie {@code null} si le fichier n'existe pas / plus.
     *
     * @throws IllegalArgumentException si le chemin sort du répertoire de stockage
     */
    public StoredFile load(String webPath) {
        if (webPath == null || !webPath.startsWith("/uploads/")) {
            throw new IllegalArgumentException("Chemin de fichier invalide.");
        }
        Path file = root.resolve(webPath.substring("/uploads/".length())).normalize();
        if (!file.startsWith(root)) {
            throw new IllegalArgumentException("Chemin de fichier invalide.");
        }
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            return null;
        }
        try {
            return new StoredFile(Files.readAllBytes(file), contentTypeFor(file));
        } catch (IOException e) {
            throw new UncheckedIOException("Échec de lecture du fichier.", e);
        }
    }

    private String contentTypeFor(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }
}
