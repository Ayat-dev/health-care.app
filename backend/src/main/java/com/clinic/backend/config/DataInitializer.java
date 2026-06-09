package com.clinic.backend.config;

import com.clinic.backend.model.User;
import com.clinic.backend.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initData(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.count() > 0) return;

            userRepository.save(new User("admin",
                    passwordEncoder.encode("admin123"), "Administrateur", "ADMIN"));
            userRepository.save(new User("dr.martin",
                    passwordEncoder.encode("medecin123"), "Dr. Martin", "MEDECIN"));
            userRepository.save(new User("secretaire",
                    passwordEncoder.encode("secretaire123"), "Secrétaire", "SECRETAIRE"));
            userRepository.save(new User("pharmacien",
                    passwordEncoder.encode("pharmacien123"), "Pharmacien", "PHARMACIEN"));
        };
    }
}
