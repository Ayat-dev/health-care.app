package com.clinic.backend.controller.api;

import com.clinic.backend.dto.ConsultationDraftDto;
import com.clinic.backend.scribe.ScribeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Scribe IA ambiant (P4.1) — REST. Transcription libre → note clinique
 * structurée via un modèle Claude. Destiné au client lourd (JavaFX) ; le
 * formulaire web passe par {@code /consultations/scribe} (chaîne session).
 *
 * <p>Erreurs mappées par le {@code GlobalExceptionHandler} : transcription
 * invalide → 400, service indisponible/désactivé → 409.
 */
@RestController
@RequestMapping("/api/scribe")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MEDECIN','ADMIN')")
public class ScribeApiController {

    private final ScribeService scribeService;

    public record StructureRequest(String transcript) {}

    @PostMapping("/structure")
    public ConsultationDraftDto structure(@RequestBody StructureRequest req) {
        return scribeService.structure(req.transcript());
    }
}
