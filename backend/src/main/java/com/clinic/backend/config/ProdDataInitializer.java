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

    @Bean
    CommandLineRunner initProdAdmin(UserRepository userRepository,
                                    ClinicRepository clinicRepository,
                                    PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.count() > 0) {
                return; // base déjà initialisée
            }
            if (adminUsername.isBlank() || adminPassword.isBlank()) {
                log.error("Aucun utilisateur en base et CLINIC_ADMIN_USERNAME / "
                        + "CLINIC_ADMIN_PASSWORD non définis. Définissez ces variables "
                        + "d'environnement pour créer le compte administrateur initial, "
                        + "puis redémarrez l'application.");
                return;
            }
            // Multi-tenant (P4.2) : une clinique par défaut, à laquelle l'admin est rattaché.
            // Le provisionnement de cliniques supplémentaires / d'un SUPER_ADMIN se fait ensuite
            // (registre /admin/clinics) — voir backlog.
            Clinic clinic = clinicRepository.findByCodeIgnoreCase("PRINCIPALE")
                    .orElseGet(() -> clinicRepository.save(new Clinic("PRINCIPALE", clinicName)));

            User admin = new User(
                    adminUsername,
                    passwordEncoder.encode(adminPassword),
                    "Administrateur",
                    "ADMIN");
            admin.setClinicId(clinic.getId());
            userRepository.save(admin);
            log.info("Compte administrateur initial créé : {} (ADMIN). "
                    + "Pensez à changer le mot de passe après la première connexion.",
                    adminUsername);
        };
    }
}
