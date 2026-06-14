package com.clinic.backend.config;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception levée quand une ressource demandée n'existe pas ou a été supprimée (soft).
 * Produit automatiquement un 404 via GlobalExceptionHandler.
 *
 * Usage :
 *   throw new ResourceNotFoundException("Patient", id);
 *   throw new ResourceNotFoundException("Patient non trouvé avec le numéro : " + recordNumber);
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String entity, Long id) {
        super(entity + " introuvable (id=" + id + ")");
    }

    public ResourceNotFoundException(String entity, String identifier) {
        super(entity + " introuvable : " + identifier);
    }
}
