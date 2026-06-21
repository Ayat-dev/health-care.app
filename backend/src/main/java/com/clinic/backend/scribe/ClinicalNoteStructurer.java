package com.clinic.backend.scribe;

import com.clinic.backend.dto.ConsultationDraftDto;

/**
 * Étage de structuration du scribe IA (P4.1) : transforme une transcription
 * libre en {@link ConsultationDraftDto}. Abstraction délibérée — elle isole
 * l'appel au modèle Claude pour que {@link ScribeService} (gardes, validation)
 * soit testable sans réseau via un stub.
 */
@FunctionalInterface
public interface ClinicalNoteStructurer {

    /**
     * @param transcript transcription non vide de la consultation
     * @return note clinique structurée (proposition, à valider par le médecin)
     * @throws IllegalStateException si le service IA est indisponible
     */
    ConsultationDraftDto structure(String transcript);
}
