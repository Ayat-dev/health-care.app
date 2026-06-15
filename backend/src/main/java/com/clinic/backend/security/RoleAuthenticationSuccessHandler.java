package com.clinic.backend.security;

import com.clinic.backend.config.RoleProfile;
import com.clinic.backend.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Redirige l'utilisateur vers la page d'accueil définie par son {@link RoleProfile}
 * immédiatement après une authentification web réussie.
 * <p>
 * Remplace le {@code defaultSuccessUrl} générique de {@code SecurityConfig} :
 * un pharmacien atterrit sur {@code /pharmacy}, un laborantin sur {@code /lab}, etc.
 */
@Component
@Slf4j
public class RoleAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        String homepage = "/dashboard"; // fallback sécurisé
        Object principal = authentication.getPrincipal();
        if (principal instanceof User user) {
            RoleProfile profile = RoleProfile.fromRole(user.getRole());
            homepage = profile.homepage;
            log.debug("Login OK — {} ({}) → {}", user.getUsername(), user.getRole(), homepage);
        }
        response.sendRedirect(request.getContextPath() + homepage);
    }
}
