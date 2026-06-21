package com.clinic.backend.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Note clinique structurée proposée par le scribe IA (P4.1) à partir d'une
 * transcription libre. Sert à la fois de <em>cible de sortie structurée</em> du
 * modèle Claude (schéma JSON dérivé de cette classe, tous les champs requis) et
 * de contrat JSON renvoyé au formulaire pour pré-remplissage.
 *
 * <p><strong>Tous les champs sont des chaînes</strong>, y compris les constantes
 * vitales — c'est volontaire : un schéma à champs requis force le modèle à
 * produire chaque clé ; en texte, une valeur absente devient une chaîne vide
 * plutôt que d'<em>obliger le modèle à inventer un nombre</em>. La conversion en
 * valeurs typées est laissée au médecin qui relit et valide (jamais d'écriture
 * automatique dans le dossier).
 */
@Getter @Setter @NoArgsConstructor
public class ConsultationDraftDto {

    @JsonPropertyDescription("Motif de consultation, en une phrase. Chaîne vide si non évoqué.")
    private String chiefComplaint = "";

    @JsonPropertyDescription("Anamnèse / histoire de la maladie actuelle. Chaîne vide si non évoquée.")
    private String history = "";

    @JsonPropertyDescription("Constatations de l'examen physique. Chaîne vide si non évoquées.")
    private String physicalExam = "";

    @JsonPropertyDescription("Diagnostic ou hypothèse diagnostique formulée par le médecin. Chaîne vide si non évoqué.")
    private String diagnosis = "";

    @JsonPropertyDescription("Codes CIM-10 pertinents séparés par des virgules, uniquement si le diagnostic est clair (ex: 'J06.9, R50.9'). Chaîne vide sinon.")
    private String icd10Codes = "";

    @JsonPropertyDescription("Conduite à tenir / plan de traitement. Chaîne vide si non évoqué.")
    private String treatmentPlan = "";

    // ── Constantes vitales : valeur numérique seule si explicitement énoncée, sinon "" ──
    @JsonPropertyDescription("Poids en kg, nombre seul (ex: '72.5'). Chaîne vide si non énoncé.")
    private String weightKg = "";

    @JsonPropertyDescription("Taille en cm, nombre seul (ex: '178'). Chaîne vide si non énoncée.")
    private String heightCm = "";

    @JsonPropertyDescription("Température en °C, nombre seul (ex: '37.2'). Chaîne vide si non énoncée.")
    private String temperatureC = "";

    @JsonPropertyDescription("Pouls en battements/min, nombre seul (ex: '78'). Chaîne vide si non énoncé.")
    private String pulseBpm = "";

    @JsonPropertyDescription("Tension artérielle systolique en mmHg, nombre seul (ex: '120'). Chaîne vide si non énoncée.")
    private String bpSystolic = "";

    @JsonPropertyDescription("Tension artérielle diastolique en mmHg, nombre seul (ex: '80'). Chaîne vide si non énoncée.")
    private String bpDiastolic = "";

    @JsonPropertyDescription("Saturation en oxygène SpO2 en %, nombre seul (ex: '98'). Chaîne vide si non énoncée.")
    private String spo2Percent = "";

    @JsonPropertyDescription("Fréquence respiratoire en cycles/min, nombre seul (ex: '16'). Chaîne vide si non énoncée.")
    private String respiratoryRate = "";
}
