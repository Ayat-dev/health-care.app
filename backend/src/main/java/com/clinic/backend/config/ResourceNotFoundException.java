package com.clinic.backend.config;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception levée quand une ressource demandée (par son id) n'existe pas ou a été
 * supprimée (soft). Produit un 404 via {@code GlobalExceptionHandler} (handler le plus
 * spécifique l'emporte sur celui d'{@code IllegalArgumentException}→400).
 * <p>
 * <b>Étend volontairement {@link IllegalArgumentException}</b> (P1.4b) : avant cette
 * exception, tous les lookups not-found levaient {@code IllegalArgumentException} et de
 * nombreux {@code WebController} l'attrapent pour afficher un flash. En héritant d'elle,
 * on bascule l'API en 404 propre <em>sans</em> casser un seul de ces {@code catch}.
 * <p>
 * Réservée aux lookups <em>par id</em> (ressource adressée absente). Les références FK
 * invalides dans un body de création/màj restent en {@code IllegalArgumentException}
 * (validation de requête → 400).
 *
 * Usage :
 *   throw new ResourceNotFoundException("Patient", id);
 *   throw new ResourceNotFoundException("Patient non trouvé avec le numéro : " + recordNumber);
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends IllegalArgumentException {

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
