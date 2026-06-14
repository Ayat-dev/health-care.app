package com.clinic.backend.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gestionnaire global d'exceptions pour tous les endpoints /api/**.
 *
 * Format de réponse standardisé :
 * {
 *   "timestamp": "...",
 *   "status": 4xx/5xx,
 *   "error": "...",
 *   "message": "...",
 *   "path": "..."
 * }
 */
@RestControllerAdvice(basePackages = "com.clinic.backend.controller.api")
@Slf4j
public class GlobalExceptionHandler {

    // ─── 400 — Validation Bean (@Valid) ─────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));

        return error(HttpStatus.BAD_REQUEST, "Validation échouée : " + details, request);
    }

    // ─── 400 — Règle métier violée ───────────────────────────────────────────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request) {
        log.debug("Règle métier : {}", ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(
            IllegalStateException ex,
            HttpServletRequest request) {
        log.debug("État invalide : {}", ex.getMessage());
        return error(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    // ─── 401 — Non authentifié ───────────────────────────────────────────────

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuth(
            AuthenticationException ex,
            HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, "Authentification requise", request);
    }

    // ─── 403 — Accès interdit ────────────────────────────────────────────────

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(
            AccessDeniedException ex,
            HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, "Accès refusé — droits insuffisants", request);
    }

    // ─── 404 — Ressource introuvable ─────────────────────────────────────────

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    // ─── 500 — Erreur inattendue ─────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(
            Exception ex,
            HttpServletRequest request) {
        log.error("Erreur inattendue sur {} : {}", request.getRequestURI(), ex.getMessage(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR,
                "Erreur interne du serveur. Contactez l'administrateur.", request);
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> error(
            HttpStatus status, String message, HttpServletRequest request) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", request.getRequestURI());

        return ResponseEntity.status(status).body(body);
    }
}
