package com.clinic.backend.controller;

import com.clinic.backend.model.User;
import com.clinic.backend.repository.UserRepository;
import com.clinic.backend.security.JwtService;
import com.clinic.backend.security.RefreshTokenService;
import com.clinic.backend.service.LoginAttemptService;
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

    public AuthController(AuthenticationManager authenticationManager,
                          UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService,
                          RefreshTokenService refreshTokenService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
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
        body.put("refreshToken", refreshToken);
        body.put("tokenType", "Bearer");
        body.put("username", user.getUsername());
        body.put("role", user.getRole());
        body.put("fullName", user.getFullName() != null ? user.getFullName() : "");
        return ResponseEntity.ok(body);
    }

    /**
     * Échange un refresh token contre un nouvel access token (+ refresh token rotaté).
     * Appelable sans access token valide (il peut être expiré) → endpoint public.
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh(@RequestBody Map<String, String> data) {
        String refreshToken = data.get("refreshToken");
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
            body.put("refreshToken", result.newRefreshToken());
            body.put("tokenType", "Bearer");
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
    }

    /** Déconnexion : révoque le refresh token fourni (idempotent). Endpoint public. */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(@RequestBody Map<String, String> data) {
        String refreshToken = data.get("refreshToken");
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.revoke(refreshToken);
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
