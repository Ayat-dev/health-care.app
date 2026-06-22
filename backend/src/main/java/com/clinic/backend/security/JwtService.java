package com.clinic.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Access token court (P4.4) portant la version de token ({@code tv}) de l'utilisateur :
     * {@code JwtFilter} la compare à {@code users.token_version} pour une révocation immédiate.
     */
    public String generateToken(String username, String role, int tokenVersion) {
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .claim("tv", tokenVersion)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(signingKey())
                .compact();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    /** Version de token portée par le claim {@code tv} (0 si absent — tokens legacy). */
    public int extractTokenVersion(String token) {
        Object tv = parseClaims(token).get("tv");
        return tv == null ? 0 : ((Number) tv).intValue();
    }

    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
