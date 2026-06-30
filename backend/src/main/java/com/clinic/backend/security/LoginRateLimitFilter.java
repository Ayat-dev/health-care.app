package com.clinic.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * D1d — filtre qui plafonne par IP les POST de login ({@code /login} web et
 * {@code /api/auth/login} API), avant l'authentification. Au-delà de la limite → 429 +
 * {@code Retry-After}, sans toucher l'{@code AuthenticationManager} (donc sans charge DB).
 *
 * <p>Instancié et inséré dans les deux chaînes par {@code SecurityConfig} (pas un bean Spring,
 * pour éviter l'auto-enregistrement de Boot qui le ferait tourner deux fois et compter double).
 */
@Slf4j
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final String WEB_LOGIN = "/login";
    private static final String API_LOGIN = "/api/auth/login";

    private final LoginRateLimiter rateLimiter;

    public LoginRateLimitFilter(LoginRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (isLoginAttempt(request)) {
            String ip = clientIp(request);
            if (!rateLimiter.tryAcquire(ip)) {
                log.warn("Login bloqué (rate-limit IP) depuis {} sur {}", ip, request.getRequestURI());
                reject(response);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private boolean isLoginAttempt(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) return false;
        String uri = request.getRequestURI();
        return WEB_LOGIN.equals(uri) || API_LOGIN.equals(uri);
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(rateLimiter.windowSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"error\":\"Trop de tentatives de connexion. Réessayez plus tard.\"}");
    }

    /** IP cliente, en tenant compte d'un proxy inverse ({@code X-Forwarded-For}). */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
