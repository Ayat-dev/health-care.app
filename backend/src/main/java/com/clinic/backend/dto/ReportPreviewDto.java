package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aperçu d'un export de rapport avant téléchargement : la liste des <b>sections</b>
 * (rubriques) disponibles, chacune avec sa table et un état coché/décoché. L'utilisateur
 * choisit les rubriques à inclure dans le .xlsx. Rendu par
 * {@code templates/export/report-preview.html}.
 */
@Getter @Setter
public class ReportPreviewDto {

    /** Une rubrique sélectionnable (KPIs, par département, par âge…). */
    @Getter @Setter
    public static class Section {
        private String key;
        private String label;
        private boolean selected;
        private List<String> headers = new ArrayList<>();
        private List<List<Object>> rows = new ArrayList<>();
    }

    private String title;
    private String periodLabel = "";
    private String previewUrl;
    private String downloadUrl;
    /** Filtres de contexte (month/year) réinjectés en champs cachés du formulaire. */
    private Map<String, String> context = new LinkedHashMap<>();
    private List<Section> sections = new ArrayList<>();
}
