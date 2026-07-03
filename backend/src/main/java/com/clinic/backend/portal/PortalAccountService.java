package com.clinic.backend.portal;

import com.clinic.backend.audit.AuditService;
import com.clinic.backend.config.ResourceNotFoundException;
import com.clinic.backend.model.Role;
import com.clinic.backend.model.User;
import com.clinic.backend.patient.Patient;
import com.clinic.backend.patient.PatientRepository;
import com.clinic.backend.repository.UserRepository;
import com.clinic.backend.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

/**
 * Onboarding du compte portail d'un patient (rôle {@code PATIENT}) : le staff crée et
 * <b>rattache</b> un compte au dossier, avec un mot de passe temporaire à remettre au
 * patient (qui le changera via {@code /portal/profile/password}). Un dossier n'a qu'un
 * seul compte portail ({@link Patient#getPortalUser()}).
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PortalAccountService {

    private static final SecureRandom RANDOM = new SecureRandom();
    // Sans caractères ambigus (0/O, 1/l/I) pour un mot de passe dicté/recopié à la main.
    private static final String LETTERS = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    /** Identifiants remis au patient (le mot de passe n'est visible qu'une fois). */
    public record Credentials(String username, String tempPassword) {}

    /** État du compte portail pour l'affichage sur la fiche patient. */
    public record Status(boolean activated, String username, boolean active) {}

    @Transactional(readOnly = true)
    public Status status(Long patientId) {
        Patient p = load(patientId);
        User u = p.getPortalUser();
        if (u == null) return new Status(false, null, false);
        return new Status(true, u.getUsername(), u.isActive());
    }

    /** Crée le compte portail et le lie au dossier. Rejette si déjà activé. */
    public Credentials activate(Long patientId) {
        Patient p = load(patientId);
        if (p.getPortalUser() != null) {
            throw new IllegalStateException("Un accès portail existe déjà pour ce patient.");
        }
        String username = uniqueUsername(p);
        String temp = randomPassword();
        User u = new User(username, passwordEncoder.encode(temp), p.getFullName(), Role.PATIENT.name());
        u.setActive(true);
        u.setClinicId(TenantContext.currentClinicId());
        User saved = userRepository.save(u);
        p.setPortalUser(saved);
        patientRepository.save(p);

        auditService.record("CREATE", "User", saved.getId(), "portalPatient=" + p.getRecordNumber());
        log.info("Accès portail activé pour le patient {} (compte {})", p.getRecordNumber(), username);
        return new Credentials(username, temp);
    }

    /** Régénère un mot de passe temporaire (le patient a oublié le sien). */
    public Credentials resetPassword(Long patientId) {
        User u = requirePortalUser(patientId);
        String temp = randomPassword();
        u.setPassword(passwordEncoder.encode(temp));
        u.bumpTokenVersion(); // coupe les sessions/jetons en cours
        userRepository.save(u);
        auditService.record("PASSWORD_CHANGE", "User", u.getId(), "portalReset");
        return new Credentials(u.getUsername(), temp);
    }

    /** Active/désactive l'accès sans supprimer le compte (conserve l'historique). */
    public void setActive(Long patientId, boolean active) {
        User u = requirePortalUser(patientId);
        u.setActive(active);
        if (!active) u.bumpTokenVersion();
        userRepository.save(u);
        auditService.record("TOGGLE_ACTIVE", "User", u.getId(), "portalActive=" + active);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────
    private Patient load(Long patientId) {
        return patientRepository.findByIdAndDeletedAtIsNull(patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));
    }

    private User requirePortalUser(Long patientId) {
        User u = load(patientId).getPortalUser();
        if (u == null) throw new IllegalStateException("Aucun accès portail n'est activé pour ce patient.");
        return u;
    }

    /** Identifiant dérivé du n° de dossier (unique, stable), suffixé si déjà pris. */
    private String uniqueUsername(Patient p) {
        String base = p.getRecordNumber() != null ? p.getRecordNumber().toLowerCase() : ("patient" + p.getId());
        String candidate = base;
        int i = 2;
        while (userRepository.existsByUsername(candidate)) {
            candidate = base + "-" + i++;
        }
        return candidate;
    }

    /** 8 caractères, ≥ 1 chiffre (respecte la politique de mot de passe), sans ambiguïté. */
    private static String randomPassword() {
        StringBuilder sb = new StringBuilder();
        sb.append(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
        String pool = LETTERS + DIGITS;
        for (int i = 0; i < 7; i++) sb.append(pool.charAt(RANDOM.nextInt(pool.length())));
        return sb.toString();
    }
}
