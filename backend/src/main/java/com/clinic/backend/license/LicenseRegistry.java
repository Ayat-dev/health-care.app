package com.clinic.backend.license;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Registre local (côté éditeur) des licences émises, au format <b>JSON Lines</b> (un objet
 * {@link LicenseRecord} par ligne). Volontairement sans dépendance Spring, réutilisable par
 * {@link LicenseKeyTool}. Fichier append-only : chaque émission ajoute une ligne, jamais de
 * réécriture — l'historique reste intact.
 */
public final class LicenseRegistry {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final Path file;

    public LicenseRegistry(Path file) {
        this.file = file;
    }

    /** Ajoute une licence au registre (crée le fichier/dossier au besoin). */
    public void append(LicenseRecord record) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            String line = MAPPER.writeValueAsString(record) + System.lineSeparator();
            Files.writeString(file, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("Écriture du registre de licences impossible", e);
        }
    }

    /** Toutes les licences émises (liste vide si le registre n'existe pas encore). */
    public List<LicenseRecord> readAll() {
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try {
            List<LicenseRecord> records = new ArrayList<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    records.add(MAPPER.readValue(line, LicenseRecord.class));
                }
            }
            return records;
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture du registre de licences impossible", e);
        }
    }
}
