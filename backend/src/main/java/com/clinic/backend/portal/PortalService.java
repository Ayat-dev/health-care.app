package com.clinic.backend.portal;

import com.clinic.backend.config.ResourceNotFoundException;
import com.clinic.backend.model.User;
import com.clinic.backend.patient.Patient;
import com.clinic.backend.patient.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Résout le dossier {@link Patient} rattaché au compte portail (rôle {@code PATIENT})
 * actuellement connecté. Toute la lecture du portail passe par ce point d'entrée :
 * un patient ne voit jamais que son propre dossier.
 */
@Service
@RequiredArgsConstructor
public class PortalService {

    private final PatientRepository patientRepository;

    /**
     * Dossier patient lié à l'utilisateur courant.
     * @throws IllegalStateException si le compte n'est rattaché à aucun dossier
     *         (compte portail non configuré par l'administration).
     */
    @Transactional(readOnly = true)
    public Patient currentPatient() {
        User user = currentUser();
        if (user == null) {
            throw new IllegalStateException("Aucun utilisateur authentifié.");
        }
        return patientRepository.findByPortalUserIdAndDeletedAtIsNull(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucun dossier patient n'est rattaché à votre compte. "
                        + "Contactez l'accueil de la clinique."));
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object principal = auth.getPrincipal();
        return principal instanceof User u ? u : null;
    }
}
