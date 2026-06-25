package com.clinic.backend.setup;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Tant que l'application n'est pas installée (aucun utilisateur en base), redirige
 * toute requête web vers l'assistant de première installation {@code /setup}.
 * <p>
 * Les chemins d'infrastructure et l'assistant lui-même sont exclus à l'enregistrement
 * (voir {@code WebConfig#addInterceptors}) — statiques, {@code /error}, {@code /api/**},
 * {@code /fhir/**}, {@code /ws/**}, {@code /actuator/**}, {@code /h2-console/**}, {@code /setup/**}.
 * Le verrouillage inverse (interdire {@code /setup} une fois installé) est géré par
 * {@link com.clinic.backend.controller.web.SetupWebController}, qui redirige alors vers {@code /login}.
 */
@RequiredArgsConstructor
public class SetupGuardInterceptor implements HandlerInterceptor {

    private final SetupService setupService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (setupService.isSetupRequired()) {
            response.setStatus(HttpServletResponse.SC_FOUND); // 302
            response.setHeader("Location", request.getContextPath() + "/setup");
            return false;
        }
        return true;
    }
}
