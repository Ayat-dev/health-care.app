package com.clinic.backend.service;

import com.clinic.backend.model.User;
import com.clinic.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

/**
 * Invariants anti-brute-force (P1.3), testés en isolation (sans Spring).
 */
@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock UserRepository userRepository;
    @InjectMocks LoginAttemptService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("bob", "hash", "Bob", "MEDECIN");
        lenient().when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));
    }

    @Test
    void incremente_le_compteur_a_chaque_echec() {
        service.loginFailed("bob");
        assertThat(user.getFailedAttempts()).isEqualTo(1);
        assertThat(user.isAccountNonLocked()).isTrue();
    }

    @Test
    void verrouille_apres_cinq_echecs() {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            service.loginFailed("bob");
        }
        assertThat(user.getFailedAttempts()).isEqualTo(LoginAttemptService.MAX_ATTEMPTS);
        assertThat(user.getLockedUntil()).isNotNull();
        assertThat(user.isAccountNonLocked()).isFalse();
    }

    @Test
    void le_succes_remet_tout_a_zero() {
        for (int i = 0; i < LoginAttemptService.MAX_ATTEMPTS; i++) {
            service.loginFailed("bob");
        }
        service.loginSucceeded("bob");
        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.isAccountNonLocked()).isTrue();
    }

    @Test
    void un_verrou_expire_repart_de_zero() {
        user.setFailedAttempts(5);
        user.setLockedUntil(LocalDateTime.now().minusMinutes(1)); // verrou déjà expiré
        assertThat(user.isAccountNonLocked()).isTrue();           // expiré → déverrouillé

        service.loginFailed("bob");
        assertThat(user.getFailedAttempts()).isEqualTo(1);        // compteur réinitialisé puis +1
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void utilisateur_inconnu_ne_casse_rien() {
        service.loginFailed("personne");   // findByUsername → empty
        service.loginSucceeded(null);       // username null
        // aucune exception attendue
    }
}
