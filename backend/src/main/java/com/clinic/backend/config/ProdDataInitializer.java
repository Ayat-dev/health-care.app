package com.clinic.backend.config;

import com.clinic.backend.model.User;
import com.clinic.backend.repository.UserRepository;
import com.clinic.backend.tenant.Clinic;
import com.clinic.backend.tenant.ClinicRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Amorçage minimal en production : crée UN seul compte administrateur à partir
 * des variables d'environnement {@code CLINIC_ADMIN_USERNAME} /
 * {@code CLINIC_ADMIN_PASSWORD}, uniquement si la base ne contient encore
 * aucun utilisateur.
 * <p>
 * <b>Chemin d'installation optionnel (headless).</b> Le chemin nominal est désormais
 * l'assistant web de première installation ({@code /setup}, voir
 * {@link com.clinic.backend.setup.SetupService}) : un admin non technique configure
 * tout depuis le navigateur, sans variable d'environnement. Ce bean reste pour les
 * déploiements scriptés/automatisés — s'il crée l'admin, des utilisateurs existent
 * donc l'assistant se désactive de lui-même. Si les variables ne sont pas fournies,
 * l'assistant {@code /setup} prend le relais au premier accès web.
 * <p>
 * Aucune donnée de démonstration n'est insérée en prod (cf. {@link DataInitializer},
 * gated {@code @Profile("!prod")}). Si les variables ne sont pas fournies au
 * premier démarrage, on logge une erreur explicite et on n'invente PAS de
 * mot de passe par défaut faible.
 */
@Configuration
@Profile("prod")
@Slf4j
public class ProdDataInitializer {

    @Value("${clinic.admin.username:}")
    private String adminUsername;

    @Value("${clinic.admin.password:}")
    private String adminPassword;

    @Value("${clinic.name:Clinique principale}")
    private String clinicName;

    // SUPER_ADMIN transverse (multi-tenant P4.2) — gère le registre des cliniques (/admin/clinics).
    // Optionnel : si fourni, on l'amorce ; il pourra ensuite créer les cliniques + leurs admins depuis l'UI.
    @Value("${clinic.superadmin.username:}")
    private String superAdminUsername;

    @Value("${clinic.superadmin.password:}")
    private String superAdminPassword;

    @Bean
    CommandLineRunner initProdAdmin(UserRepository userRepository,
                                    ClinicRepository clinicRepository,
                                    PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.count() > 0) {
                return; // base déjà initialisée
            }
            boolean hasClinicAdmin = !adminUsername.isBlank() && !adminPassword.isBlank();
            boolean hasSuperAdmin = !superAdminUsername.isBlank() && !superAdminPassword.isBlank();

            if (!hasClinicAdmin && !hasSuperAdmin) {
                log.error("Aucun utilisateur en base et aucun compte initial défini. Définissez "
                        + "CLINIC_ADMIN_USERNAME/CLINIC_ADMIN_PASSWORD (admin de clinique) et/ou "
                        + "CLINIC_SUPERADMIN_USERNAME/CLINIC_SUPERADMIN_PASSWORD (super-admin transverse), "
                        + "puis redémarrez. (Sinon l'assistant web /setup prend le relais.)");
                return;
            }

            // SUPER_ADMIN : transverse → clinic_id NULL (ne pas rattacher à une clinique).
            if (hasSuperAdmin) {
                User superAdmin = new User(
                        superAdminUsername,
                        passwordEncoder.encode(superAdminPassword),
                        "Super administrateur",
                        "SUPER_ADMIN");
                userRepository.save(superAdmin);
                log.info("Compte SUPER_ADMIN initial créé : {}. Il peut créer les cliniques et leurs "
                        + "administrateurs depuis /admin/clinics. Changez le mot de passe à la première connexion.",
                        superAdminUsername);
            }

            // ADMIN de clinique : une clinique par défaut, à laquelle l'admin est rattaché.
            if (hasClinicAdmin) {
                Clinic clinic = clinicRepository.findByCodeIgnoreCase("PRINCIPALE")
                        .orElseGet(() -> clinicRepository.save(new Clinic("PRINCIPALE", clinicName)));

                User admin = new User(
                        adminUsername,
                        passwordEncoder.encode(adminPassword),
                        "Administrateur",
                        "ADMIN");
                admin.setClinicId(clinic.getId());
                userRepository.save(admin);
                log.info("Compte administrateur initial créé : {} (ADMIN, clinique {}). "
                        + "Pensez à changer le mot de passe après la première connexion.",
                        adminUsername, clinic.getCode());
            }
        };
    }
}
