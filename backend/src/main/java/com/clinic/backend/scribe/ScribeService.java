package com.clinic.backend.scribe;

import com.clinic.backend.dto.ConsultationDraftDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Orchestration du scribe IA (P4.1) : valide l'entrée, applique le drapeau
 * d'activation, délègue la structuration au {@link ClinicalNoteStructurer}.
 *
 * <p>La sortie est une <em>proposition</em> de pré-remplissage : aucune écriture
 * automatique dans le dossier — le médecin relit, corrige et enregistre lui-même
 * (sécurité clinique + traçabilité via le journal d'audit P1.2).
 */
@Service
public class ScribeService {

    /** Garde-fou : transcription d'une consultation, pas un transcript d'heures. */
    private static final int MAX_TRANSCRIPT_CHARS = 20_000;

    private final ClinicalNoteStructurer structurer;
    private final boolean enabled;

    public ScribeService(ClinicalNoteStructurer structurer,
                         @Value("${app.scribe.enabled:false}") boolean enabled) {
        this.structurer = structurer;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public ConsultationDraftDto structure(String transcript) {
        if (!enabled) {
            throw new IllegalStateException("Le scribe IA est désactivé sur cette instance.");
        }
        if (!StringUtils.hasText(transcript)) {
            throw new IllegalArgumentException("La transcription est vide.");
        }
        if (transcript.length() > MAX_TRANSCRIPT_CHARS) {
            throw new IllegalArgumentException(
                "Transcription trop longue (max " + MAX_TRANSCRIPT_CHARS + " caractères).");
        }
        return structurer.structure(transcript.trim());
    }
}
