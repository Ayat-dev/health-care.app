package com.clinic.backend.security;

import com.clinic.backend.service.LoginAttemptService;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * Relie les événements d'authentification Spring Security au {@link LoginAttemptService}
 * (anti-brute-force, P1.3).
 * <p>
 * On n'écoute que {@code BadCredentials} : un compte déjà verrouillé déclenche un
 * {@code AuthenticationFailureLockedEvent} distinct qu'on ignore volontairement (sinon
 * le verrou se prolongerait à chaque tentative). Les événements ne sont publiés que par
 * l'{@code AuthenticationManager} (login web + {@code /api/auth/login}) ; le {@code JwtFilter}
 * pose l'authentification directement et n'en émet pas → pas de bruit par requête API.
 */
@Component
public class AuthenticationEventListener {

    private final LoginAttemptService loginAttemptService;

    public AuthenticationEventListener(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        loginAttemptService.loginSucceeded(event.getAuthentication().getName());
    }

    @EventListener
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        // event.getAuthentication().getName() = le username tenté (même si l'utilisateur n'existe pas)
        loginAttemptService.loginFailed(event.getAuthentication().getName());
    }
}
