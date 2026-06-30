package com.clinic.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * D1a — gestion du refresh token en cookie {@code HttpOnly} pour le front web.
 *
 * <p>Les clients API/desktop reçoivent le refresh token en JSON (inchangé). Un client
 * <b>navigateur</b> opte pour le mode cookie : le refresh token est alors posé en cookie
 * {@code HttpOnly}/{@code SameSite=Strict}/{@code Secure} — jamais accessible au JavaScript —
 * et l'access token (court) reste le seul jeton manipulé côté page. Le cookie est limité au
 * chemin {@code /api/auth} (envoyé uniquement à {@code /refresh} et {@code /logout}).
 */
@Component
public class RefreshCookieManager {

    /** Nom du cookie porteur du refresh token (web). */
    public static final String COOKIE_NAME = "refresh_token";
    /** Cookie restreint aux endpoints d'auth (refresh + logout). */
    private static final String COOKIE_PATH = "/api/auth";

    private final long refreshExpirationMs;
    private final boolean secure;

    public RefreshCookieManager(
            @Value("${app.jwt.refresh-expiration-ms}") long refreshExpirationMs,
            @Value("${app.jwt.refresh-cookie-secure}") boolean secure) {
        this.refreshExpirationMs = refreshExpirationMs;
        this.secure = secure;
    }

    /** Cookie de pose du refresh token (durée = validité du refresh). */
    public String buildSetCookie(String rawRefreshToken) {
        return ResponseCookie.from(COOKIE_NAME, rawRefreshToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(Duration.ofMillis(refreshExpirationMs))
                .build()
                .toString();
    }

    /** Cookie d'effacement (max-age 0) — utilisé au logout. */
    public String buildClearCookie() {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(0)
                .build()
                .toString();
    }

    /** Lit le refresh token depuis le cookie de la requête, s'il est présent et non vide. */
    public Optional<String> readFromRequest(HttpServletRequest request) {
        if (request == null || request.getCookies() == null) return Optional.empty();
        for (var cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName())
                    && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    /** En-tête HTTP à utiliser pour poser/effacer le cookie. */
    public String headerName() {
        return HttpHeaders.SET_COOKIE;
    }
}
