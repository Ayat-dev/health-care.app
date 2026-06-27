package com.clinic.backend.setup;

import com.clinic.backend.clinicconfig.ClinicConfig;
import com.clinic.backend.clinicconfig.ClinicConfigRepository;
import com.clinic.backend.model.User;
import com.clinic.backend.repository.UserRepository;
import com.clinic.backend.tenant.Clinic;
import com.clinic.backend.tenant.ClinicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Première installation : tant qu'AUCUN utilisateur n'existe en base, l'application
 * est « non installée » et l'admin doit passer par l'assistant {@code /setup} pour
 * créer le compte administrateur, déclarer l'identité de la clinique et choisir les
 * modules — sans éditer de {@code .env} ni de variable d'environnement.
 * <p>
 * <b>Le signal d'installation est simplement « il existe au moins un utilisateur »</b> :
 * <ul>
 *   <li>en dev, {@code DataInitializer} seede des comptes → l'assistant est ignoré ;</li>
 *   <li>en prod headless, {@code ProdDataInitializer} peut créer l'admin depuis les
 *       variables {@code CLINIC_ADMIN_*} → l'assistant est également ignoré ;</li>
 *   <li>une base prod fraîche (Flyway a seedé {@code clinic_config} + la clinique par
 *       défaut, mais zéro utilisateur) → l'assistant s'affiche.</li>
 * </ul>
 * Aucune migration de schéma n'est donc nécessaire pour porter cet état.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SetupService {

    /** Code de la clinique par défaut (aligné sur {@code ProdDataInitializer}). */
    private static final String DEFAULT_CLINIC_CODE = "PRINCIPALE";
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository userRepository;
    private final ClinicRepository clinicRepository;
    private final ClinicConfigRepository clinicConfigRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Verrou à sens unique : une fois qu'on a constaté la présence d'utilisateurs,
     * l'installation est définitivement terminée (le nombre d'utilisateurs ne
     * retombe jamais à zéro en pratique). Évite une requête {@code COUNT} à chaque
     * requête HTTP filtrée par {@link SetupGuardInterceptor}.
     */
    private volatile boolean setupComplete = false;

    @Transactional(readOnly = true)
    public boolean isSetupRequired() {
        if (setupComplete) return false;
        boolean hasUsers = userRepository.count() > 0;
        if (hasUsers) setupComplete = true;
        return !hasUsers;
    }

    /**
     * Finalise l'installation : crée la clinique (ou réutilise celle par défaut),
     * le compte administrateur, et écrit l'identité + les modules dans le singleton
     * {@code clinic_config}. Idempotence : refuse de s'exécuter si un utilisateur
     * existe déjà (anti double-soumission / course).
     *
     * @throws IllegalArgumentException si une donnée saisie est invalide (message destiné à l'UI)
     * @throws IllegalStateException    si l'installation a déjà été effectuée
     */
    @Transactional
    public void complete(SetupForm form) {
        if (!isSetupRequired()) {
            throw new IllegalStateException("L'application est déjà installée.");
        }
        validate(form);

        // 1) Clinique (tenant) — réutilise la clinique par défaut seedée par Flyway si présente.
        Clinic clinic = clinicRepository.findByCodeIgnoreCase(DEFAULT_CLINIC_CODE)
                .orElseGet(() -> new Clinic(DEFAULT_CLINIC_CODE, form.getClinicName().trim()));
        clinic.setName(form.getClinicName().trim());
        clinic.setAddress(trimToNull(form.getClinicAddress()));
        clinic.setPhone(trimToNull(form.getClinicPhone()));
        clinic.setEmail(trimToNull(form.getClinicEmail()));
        clinic.setActive(true);
        clinic = clinicRepository.save(clinic);

        // 2) Compte administrateur initial.
        User admin = new User(
                form.getAdminUsername().trim(),
                passwordEncoder.encode(form.getAdminPassword()),
                trimToNull(form.getAdminFullName()) != null ? form.getAdminFullName().trim() : "Administrateur",
                "ADMIN");
        admin.setClinicId(clinic.getId());
        admin.setActive(true);
        userRepository.save(admin);

        // 3) Identité + modules dans la config de CETTE clinique (P4.2 : une par clinique).
        ClinicConfig config = clinicConfigRepository.findByClinicId(clinic.getId())
                .orElseGet(ClinicConfig::new);
        config.setClinicId(clinic.getId());
        config.setName(form.getClinicName().trim());
        config.setAddress(trimToNull(form.getClinicAddress()));
        config.setPhone(trimToNull(form.getClinicPhone()));
        config.setEmail(trimToNull(form.getClinicEmail()));
        if (trimToNull(form.getCurrency()) != null) config.setCurrency(form.getCurrency().trim());
        if (trimToNull(form.getDefaultLanguage()) != null) config.setDefaultLanguage(form.getDefaultLanguage().trim());
        config.setModulePharmacy(form.isModulePharmacy());
        config.setModuleLab(form.isModuleLab());
        config.setModuleMaternity(form.isModuleMaternity());
        config.setModuleRadiology(form.isModuleRadiology());
        config.setModuleHospitalization(form.isModuleHospitalization());
        config.setModuleDental(form.isModuleDental());
        clinicConfigRepository.save(config);

        setupComplete = true;
        log.info("Installation terminée : clinique « {} », administrateur « {} ».",
                clinic.getName(), admin.getUsername());
    }

    private void validate(SetupForm f) {
        if (trimToNull(f.getAdminUsername()) == null) {
            throw new IllegalArgumentException("Le nom d'utilisateur de l'administrateur est obligatoire.");
        }
        if (userRepository.existsByUsername(f.getAdminUsername().trim())) {
            throw new IllegalArgumentException("Ce nom d'utilisateur est déjà pris.");
        }
        if (trimToNull(f.getClinicName()) == null) {
            throw new IllegalArgumentException("Le nom de la clinique est obligatoire.");
        }
        String pwd = f.getAdminPassword();
        if (pwd == null || pwd.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "Le mot de passe doit contenir au moins " + MIN_PASSWORD_LENGTH + " caractères.");
        }
        if (!pwd.equals(f.getAdminPasswordConfirm())) {
            throw new IllegalArgumentException("Les deux mots de passe ne correspondent pas.");
        }
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
