package com.clinic.backend.config;

import com.clinic.backend.model.User;
import com.clinic.backend.repository.UserRepository;
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

    @Bean
    CommandLineRunner initProdAdmin(UserRepository userRepository,
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
            User admin = new User(
                    adminUsername,
                    passwordEncoder.encode(adminPassword),
                    "Administrateur",
                    "ADMIN");
            userRepository.save(admin);
            log.info("Compte administrateur initial créé : {} (ADMIN). "
                    + "Pensez à changer le mot de passe après la première connexion.",
                    adminUsername);
        };
    }
}
