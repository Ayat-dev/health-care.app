package com.clinic.backend.fhir;

import ca.uhn.fhir.context.FhirContext;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.Enumerations;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;

/**
 * Façade FHIR R4 (P2.1) — expose {@code Patient}, {@code Encounter},
 * {@code Observation} (constantes + labo) et {@code MedicationRequest} (ordonnances)
 * en lecture sous {@code /fhir/**}, sérialisés en {@code application/fhir+json} via HAPI.
 *
 * <p>Volontairement <b>hors</b> du package {@code controller.api} : les erreurs sont
 * rendues en {@code OperationOutcome} FHIR par {@link FhirExceptionHandler}, pas par le
 * {@code GlobalExceptionHandler} JSON générique. La sécurité passe par la chaîne
 * stateless JWT ({@code /fhir/**} ajouté au {@code securityMatcher}).</p>
 */
@RestController
@RequestMapping("/fhir")
@RequiredArgsConstructor
public class FhirController {

    static final String FHIR_JSON = "application/fhir+json";

    private final FhirService fhirService;
    private final FhirContext fhirContext;

    // ── CapabilityStatement ─────────────────────────────────────────────────────

    @GetMapping(value = "/metadata", produces = FHIR_JSON)
    public ResponseEntity<String> metadata() {
        CapabilityStatement cs = new CapabilityStatement();
        cs.setStatus(Enumerations.PublicationStatus.ACTIVE);
        cs.setDate(new Date());
        cs.setPublisher("ClinicApp");
        cs.setKind(CapabilityStatement.CapabilityStatementKind.INSTANCE);
        cs.setFhirVersion(Enumerations.FHIRVersion._4_0_1);
        cs.setFormat(List.of(new CodeType("application/fhir+json")));
        CapabilityStatement.CapabilityStatementRestComponent rest =
                cs.addRest().setMode(CapabilityStatement.RestfulCapabilityMode.SERVER);
        for (String type : List.of("Patient", "Encounter", "Observation", "MedicationRequest")) {
            CapabilityStatement.CapabilityStatementRestResourceComponent res = rest.addResource().setType(type);
            res.addInteraction().setCode(CapabilityStatement.TypeRestfulInteraction.READ);
            res.addInteraction().setCode(CapabilityStatement.TypeRestfulInteraction.SEARCHTYPE);
        }
        return fhirResponse(cs);
    }

    // ── Patient ───────────────────────────────────────────────────────────────

    @GetMapping(value = "/Patient/{id}", produces = FHIR_JSON)
    public ResponseEntity<String> readPatient(@PathVariable Long id) {
        return fhirResponse(fhirService.getPatient(id));
    }

    @GetMapping(value = "/Patient", produces = FHIR_JSON)
    public ResponseEntity<String> searchPatient(@RequestParam(required = false) String name) {
        return fhirResponse(fhirService.searchPatients(name));
    }

    // ── Encounter ───────────────────────────────────────────────────────────────

    @GetMapping(value = "/Encounter/{id}", produces = FHIR_JSON)
    public ResponseEntity<String> readEncounter(@PathVariable Long id) {
        return fhirResponse(fhirService.getEncounter(id));
    }

    @GetMapping(value = "/Encounter", produces = FHIR_JSON)
    public ResponseEntity<String> searchEncounter(@RequestParam Long patient) {
        return fhirResponse(fhirService.searchEncounters(patient));
    }

    // ── Observation ───────────────────────────────────────────────────────────────

    @GetMapping(value = "/Observation/{id}", produces = FHIR_JSON)
    public ResponseEntity<String> readObservation(@PathVariable String id) {
        return fhirResponse(fhirService.getObservation(id));
    }

    @GetMapping(value = "/Observation", produces = FHIR_JSON)
    public ResponseEntity<String> searchObservation(@RequestParam Long patient) {
        return fhirResponse(fhirService.searchObservations(patient));
    }

    // ── MedicationRequest ───────────────────────────────────────────────────────

    @GetMapping(value = "/MedicationRequest/{id}", produces = FHIR_JSON)
    public ResponseEntity<String> readMedicationRequest(@PathVariable Long id) {
        return fhirResponse(fhirService.getMedicationRequest(id));
    }

    @GetMapping(value = "/MedicationRequest", produces = FHIR_JSON)
    public ResponseEntity<String> searchMedicationRequest(@RequestParam Long patient) {
        return fhirResponse(fhirService.searchMedicationRequests(patient));
    }

    // ── Sérialisation ─────────────────────────────────────────────────────────

    private ResponseEntity<String> fhirResponse(IBaseResource resource) {
        // Un IParser n'est pas garanti thread-safe — créé à la demande (le FhirContext l'est).
        String body = fhirContext.newJsonParser().encodeResourceToString(resource);
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf(FHIR_JSON))
                .body(body);
    }
}
