package com.clinic.backend.scribe;

import com.clinic.backend.dto.ConsultationDraftDto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test unitaire pur (sans Spring, sans réseau) du scribe IA (P4.1). Le
 * {@link ClinicalNoteStructurer} est stubé : on vérifie les gardes et la
 * délégation, pas l'appel au modèle (validé manuellement avec une clé réelle).
 */
class ScribeServiceTest {

    private final ClinicalNoteStructurer stub = transcript -> {
        ConsultationDraftDto d = new ConsultationDraftDto();
        d.setChiefComplaint("Fièvre depuis 3 jours");
        d.setDiagnosis("Suspicion de paludisme");
        return d;
    };

    @Test
    void structures_transcript_when_enabled() {
        ScribeService svc = new ScribeService(stub, true);
        ConsultationDraftDto d = svc.structure("Le patient se plaint de fièvre depuis trois jours.");
        assertEquals("Fièvre depuis 3 jours", d.getChiefComplaint());
        assertEquals("Suspicion de paludisme", d.getDiagnosis());
    }

    @Test
    void rejects_blank_transcript() {
        ScribeService svc = new ScribeService(stub, true);
        assertThrows(IllegalArgumentException.class, () -> svc.structure("   "));
    }

    @Test
    void rejects_when_disabled() {
        ScribeService svc = new ScribeService(stub, false);
        assertThrows(IllegalStateException.class,
                () -> svc.structure("transcription valide de consultation"));
    }
}
