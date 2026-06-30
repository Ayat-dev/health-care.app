package com.clinic.backend.controller;

import com.clinic.backend.model.User;
import com.clinic.backend.repository.UserRepository;
import com.clinic.backend.security.JwtService;
import com.clinic.backend.security.RefreshCookieManager;
import com.clinic.backend.security.RefreshTokenService;
import com.clinic.backend.service.LoginAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshCookieManager refreshCookieManager;

    public AuthController(AuthenticationManager authenticationManager,
                          UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService,
                          RefreshTokenService refreshTokenService,
                          RefreshCookieManager refreshCookieManager) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.refreshCookieManager = refreshCookieManager;
    }

    @PostMapping("/register")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<User> register(@RequestBody Map<String, String> data) {
        User user = new User(
                data.get("username"),
                passwordEncoder.encode(data.get("password")),
                data.get("fullName"),
                data.getOrDefault("role", "USER")
        );
        return ResponseEntity.ok(userRepository.save(user));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody Map<String, String> data) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            data.get("username"), data.get("password")));
        } catch (LockedException e) {
            // Anti-brute-force (P1.3) : compte verrouillé → 423 Locked, message clair.
            return ResponseEntity.status(HttpStatus.LOCKED).body(Map.of(
                    "error", "Compte verrouillé après trop de tentatives. Réessayez dans "
                            + LoginAttemptService.LOCK_MINUTES + " minutes."));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "Identifiant ou mot de passe incorrect."));
        }

        User user = userRepository.findByUsername(data.get("username"))
                .orElseThrow();

        // P4.4 : access token court + refresh token long (révocable, rotatif).
        String accessToken = jwtService.generateToken(
                user.getUsername(), user.getRole(), user.getTokenVersion());
        String refreshToken = refreshTokenService.issue(user);

        Map<String, String> body = new HashMap<>();
        body.put("accessToken", accessToken);
        body.put("token", accessToken); // rétro-compat : ancien nom du champ
        body.put("tokenType", "Bearer");
        body.put("userId", String.valueOf(user.getId()));
        body.put("username", user.getUsername());
        body.put("role", user.getRole());
        body.put("fullName", user.getFullName() != null ? user.getFullName() : "");

        // D1a — front web (cookie=true) : refresh token en cookie HttpOnly, jamais en JSON
        // (donc jamais exposé au JS). API/desktop : refresh token en JSON, inchangé.
        if (wantsCookie(data)) {
            return ResponseEntity.ok()
                    .header(refreshCookieManager.headerName(),
                            refreshCookieManager.buildSetCookie(refreshToken))
                    .body(body);
        }
        body.put("refreshToken", refreshToken);
        return ResponseEntity.ok(body);
    }

    /** Le client demande le mode cookie (front web) via {@code "cookie":"true"} dans le corps. */
    private boolean wantsCookie(Map<String, String> data) {
        return "true".equalsIgnoreCase(data.get("cookie"));
    }

    /**
     * Échange un refresh token contre un nouvel access token (+ refresh token rotaté).
     * Appelable sans access token valide (il peut être expiré) → endpoint public.
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh(
            @RequestBody(required = false) Map<String, String> data,
            HttpServletRequest request) {
        // D1a — front web : le refresh token vient du cookie HttpOnly (prioritaire) ; sinon
        // du corps JSON (API/desktop). Le mode de réponse suit la provenance du jeton.
        var fromCookie = refreshCookieManager.readFromRequest(request);
        Map<String, String> payload = data != null ? data : Map.of();
        String refreshToken = fromCookie.orElseGet(() -> payload.get("refreshToken"));
        boolean cookieMode = fromCookie.isPresent();

        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "refreshToken manquant."));
        }
        try {
            RefreshTokenService.RotationResult result = refreshTokenService.rotate(refreshToken);
            User user = result.user();
            String accessToken = jwtService.generateToken(
                    user.getUsername(), user.getRole(), user.getTokenVersion());

            Map<String, String> body = new HashMap<>();
            body.put("accessToken", accessToken);
            body.put("token", accessToken);
            body.put("tokenType", "Bearer");

            if (cookieMode) {
                // Refresh rotaté reposé en cookie ; jamais renvoyé au JS.
                return ResponseEntity.ok()
                        .header(refreshCookieManager.headerName(),
                                refreshCookieManager.buildSetCookie(result.newRefreshToken()))
                        .body(body);
            }
            body.put("refreshToken", result.newRefreshToken());
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException e) {
            // Échec de rotation en mode cookie → on efface le cookie (jeton mort/volé).
            if (cookieMode) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .header(refreshCookieManager.headerName(),
                                refreshCookieManager.buildClearCookie())
                        .body(Map.of("error", e.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
    }

    /** Déconnexion : révoque le refresh token fourni (idempotent). Endpoint public. */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestBody(required = false) Map<String, String> data,
            HttpServletRequest request) {
        var fromCookie = refreshCookieManager.readFromRequest(request);
        Map<String, String> payload = data != null ? data : Map.of();
        String refreshToken = fromCookie.orElseGet(() -> payload.get("refreshToken"));
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.revoke(refreshToken);
        }
        // D1a — efface le cookie web s'il était présent (no-op pour API/desktop).
        if (fromCookie.isPresent()) {
            return ResponseEntity.ok()
                    .header(refreshCookieManager.headerName(), refreshCookieManager.buildClearCookie())
                    .body(Map.of("message", "Déconnecté."));
        }
        return ResponseEntity.ok(Map.of("message", "Déconnecté."));
    }

    /**
     * Déconnexion de TOUTES les sessions de l'utilisateur courant : révoque tous ses
     * refresh tokens et invalide immédiatement ses access tokens (bump de version).
     * Nécessite un access token valide.
     */
    @PostMapping("/logout-all")
    public ResponseEntity<Map<String, String>> logoutAll(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Non authentifié."));
        }
        refreshTokenService.revokeAllForUser(user);
        return ResponseEntity.ok(Map.of("message", "Toutes les sessions ont été révoquées."));
    }
}
