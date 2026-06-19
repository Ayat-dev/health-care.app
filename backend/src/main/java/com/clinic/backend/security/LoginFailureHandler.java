package com.clinic.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Aiguille les échecs de login web vers un message clair (P1.3) :
 * un compte verrouillé → {@code /login?locked=true} ; tout le reste → {@code /login?error=true}.
 * <p>
 * {@link LockedException} est levée par {@code DaoAuthenticationProvider} dès que
 * {@code User.isAccountNonLocked()} renvoie false (verrou non expiré).
 */
@Component
public class LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String target = (exception instanceof LockedException) ? "/login?locked=true" : "/login?error=true";
        getRedirectStrategy().sendRedirect(request, response, request.getContextPath() + target);
    }
}
