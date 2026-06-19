package com.clinic.backend.service;

import com.clinic.backend.model.User;
import com.clinic.backend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Anti-brute-force (P1.3) : compte les échecs de connexion consécutifs et
 * verrouille le compte {@value #MAX_ATTEMPTS} échecs → {@value #LOCK_MINUTES} min.
 * <p>
 * Branché sur les événements d'authentification Spring Security
 * ({@link com.clinic.backend.security.AuthenticationEventListener}) : couvre donc
 * à la fois le login web ({@code /login}) et le login API ({@code /api/auth/login}),
 * tous deux passant par l'{@code AuthenticationManager}.
 */
@Service
@Slf4j
public class LoginAttemptService {

    public static final int    MAX_ATTEMPTS = 5;
    public static final long   LOCK_MINUTES = 15;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(LOCK_MINUTES);

    private final UserRepository userRepository;

    public LoginAttemptService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Échec de mot de passe : incrémente le compteur, verrouille au-delà du seuil. */
    @Transactional
    public void loginFailed(String username) {
        if (username == null) return;
        userRepository.findByUsername(username).ifPresent(user -> {
            // Un verrou précédent expiré repart de zéro.
            if (user.getLockedUntil() != null && user.getLockedUntil().isBefore(LocalDateTime.now())) {
                user.setLockedUntil(null);
                user.setFailedAttempts(0);
            }
            int attempts = user.getFailedAttempts() + 1;
            user.setFailedAttempts(attempts);
            if (attempts >= MAX_ATTEMPTS) {
                user.setLockedUntil(LocalDateTime.now().plus(LOCK_DURATION));
                log.warn("Compte « {} » verrouillé {} min après {} échecs", username, LOCK_MINUTES, attempts);
            }
            userRepository.save(user);
        });
    }

    /** Connexion réussie : remet à zéro le compteur et lève le verrou. */
    @Transactional
    public void loginSucceeded(String username) {
        if (username == null) return;
        userRepository.findByUsername(username).ifPresent(user -> {
            if (user.getFailedAttempts() != 0 || user.getLockedUntil() != null) {
                user.setFailedAttempts(0);
                user.setLockedUntil(null);
                userRepository.save(user);
            }
        });
    }
}
