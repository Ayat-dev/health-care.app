package com.clinic.backend.fhir;

import ca.uhn.fhir.context.FhirContext;
import com.clinic.backend.config.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Rend les erreurs des endpoints {@code /fhir/**} sous forme de ressource FHIR
 * {@code OperationOutcome} (et non au format JSON générique du
 * {@code GlobalExceptionHandler}, qui ne couvre que {@code controller.api}).
 */
@RestControllerAdvice(basePackages = "com.clinic.backend.fhir")
@RequiredArgsConstructor
@Slf4j
public class FhirExceptionHandler {

    private final FhirContext fhirContext;

    /** 404 — ressource introuvable. Plus spécifique qu'{@code IllegalArgumentException}. */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<String> handleNotFound(ResourceNotFoundException ex) {
        return outcome(HttpStatus.NOT_FOUND, OperationOutcome.IssueType.NOTFOUND, ex.getMessage());
    }

    /** 400 — paramètre de recherche obligatoire manquant (ex. {@code patient}). */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<String> handleMissingParam(MissingServletRequestParameterException ex) {
        return outcome(HttpStatus.BAD_REQUEST, OperationOutcome.IssueType.REQUIRED,
                "Paramètre requis manquant : " + ex.getParameterName());
    }

    /** 400 — id/paramètre mal typé (ex. {@code /fhir/Patient/abc}). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<String> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return outcome(HttpStatus.BAD_REQUEST, OperationOutcome.IssueType.INVALID,
                "Valeur invalide pour « " + ex.getName() + " »");
    }

    /** 400 — règle métier / argument invalide. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException ex) {
        return outcome(HttpStatus.BAD_REQUEST, OperationOutcome.IssueType.INVALID, ex.getMessage());
    }

    /** 500 — inattendu. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGeneric(Exception ex) {
        log.error("Erreur FHIR inattendue : {}", ex.getMessage(), ex);
        return outcome(HttpStatus.INTERNAL_SERVER_ERROR, OperationOutcome.IssueType.EXCEPTION,
                "Erreur interne du serveur.");
    }

    private ResponseEntity<String> outcome(HttpStatus status,
                                           OperationOutcome.IssueType type, String message) {
        OperationOutcome oo = new OperationOutcome();
        oo.addIssue()
                .setSeverity(OperationOutcome.IssueSeverity.ERROR)
                .setCode(type)
                .setDiagnostics(message);
        String body = fhirContext.newJsonParser().encodeResourceToString(oo);
        return ResponseEntity.status(status)
                .contentType(MediaType.valueOf(FhirController.FHIR_JSON))
                .body(body);
    }
}
