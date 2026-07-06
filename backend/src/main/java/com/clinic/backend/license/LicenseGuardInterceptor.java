package com.clinic.backend.license;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * Applique la règle d'expiration de licence : quand l'installation est en état bloqué
 * (essai terminé ou licence expirée), l'application passe en <b>lecture seule</b>.
 * <p>
 * Décision produit (arbitrage médical) : on ne coupe JAMAIS la lecture des dossiers ni
 * l'export. Concrètement, seules les <b>méthodes d'écriture</b> (POST/PUT/PATCH/DELETE)
 * sont bloquées ; les lectures (GET/HEAD) et donc l'export (téléchargements GET) restent
 * ouverts. La page d'activation, la connexion/déconnexion et le MFA restent accessibles
 * (exclus à l'enregistrement dans {@code WebConfig}).
 * <p>
 * Sans effet si l'enforcement est désactivé ou l'état non bloqué.
 */
@RequiredArgsConstructor
public class LicenseGuardInterceptor implements HandlerInterceptor {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final LicenseService licenseService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!licenseService.isWriteBlocked()) {
            return true;
        }
        if (SAFE_METHODS.contains(request.getMethod())) {
            return true;
        }
        // Écriture en état bloqué → redirection vers la page d'activation.
        response.setStatus(HttpServletResponse.SC_FOUND); // 302
        response.setHeader("Location", request.getContextPath() + "/license?blocked");
        return false;
    }
}
