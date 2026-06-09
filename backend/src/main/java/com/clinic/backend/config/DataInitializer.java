package com.clinic.backend.config;

import com.clinic.backend.model.User;
import com.clinic.backend.patient.Patient;
import com.clinic.backend.patient.PatientRepository;
import com.clinic.backend.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initData(UserRepository userRepository,
                               PatientRepository patientRepository,
                               PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.count() > 0) return;

            // Utilisateurs
            User admin = userRepository.save(new User("admin",
                    passwordEncoder.encode("admin123"), "Administrateur", "ADMIN"));
            User doctor = userRepository.save(new User("dr.martin",
                    passwordEncoder.encode("medecin123"), "Dr. Martin", "MEDECIN"));
            userRepository.save(new User("secretaire",
                    passwordEncoder.encode("secretaire123"), "Secrétaire", "SECRETAIRE"));
            userRepository.save(new User("pharmacien",
                    passwordEncoder.encode("pharmacien123"), "Pharmacien", "PHARMACIEN"));

            // Patients de test
            Patient p1 = new Patient();
            p1.setRecordNumber("PAT-2026-00001");
            p1.setFirstName("Aminata"); p1.setLastName("Diallo");
            p1.setBirthDate(LocalDate.of(1990, 3, 15));
            p1.setGender("F"); p1.setPhone("+221 77 123 45 67");
            p1.setCity("Dakar"); p1.setBloodType("O+");
            p1.setAssignedDoctor(doctor);
            patientRepository.save(p1);

            Patient p2 = new Patient();
            p2.setRecordNumber("PAT-2026-00002");
            p2.setFirstName("Moussa"); p2.setLastName("Koné");
            p2.setBirthDate(LocalDate.of(1985, 7, 22));
            p2.setGender("M"); p2.setPhone("+221 76 987 65 43");
            p2.setCity("Abidjan"); p2.setBloodType("A+");
            p2.setAllergies("Pénicilline");
            p2.setAssignedDoctor(doctor);
            patientRepository.save(p2);

            Patient p3 = new Patient();
            p3.setRecordNumber("PAT-2026-00003");
            p3.setFirstName("Fatou"); p3.setLastName("Traoré");
            p3.setBirthDate(LocalDate.of(2001, 11, 5));
            p3.setGender("F"); p3.setPhone("+221 70 456 78 90");
            p3.setCity("Bamako");
            patientRepository.save(p3);
        };
    }
}
