package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Modèle d'aperçu d'un export Excel avant téléchargement : la liste des colonnes
 * disponibles (avec leur état coché/décoché) + un échantillon de lignes reflétant
 * la sélection courante. Rendu par {@code templates/export/preview.html}, partagé
 * par tous les exports de listes (factures, patients, RDV, dispensations).
 */
@Getter @Setter
public class ExportPreviewDto {

    /** Champ (colonne) sélectionnable. */
    @Getter @Setter
    public static class Field {
        private String key;
        private String label;
        private boolean selected;

        public Field(String key, String label, boolean selected) {
            this.key = key;
            this.label = label;
            this.selected = selected;
        }
    }

    private String title;
    /** URL de téléchargement du .xlsx (les colonnes cochées y sont postées en query). */
    private String downloadUrl;
    /** URL de rafraîchissement de l'aperçu (même page). */
    private String previewUrl;
    /** Filtres de contexte (from/to/status/q…) réinjectés en champs cachés du formulaire. */
    private Map<String, String> context = new LinkedHashMap<>();

    private List<Field> fields = new ArrayList<>();
    /** En-têtes des colonnes sélectionnées (pour le tableau d'échantillon). */
    private List<String> sampleHeaders = new ArrayList<>();
    /** Quelques lignes d'exemple (colonnes sélectionnées uniquement). */
    private List<List<Object>> sampleRows = new ArrayList<>();
    private int totalRows;
    private int sampleSize;
}
