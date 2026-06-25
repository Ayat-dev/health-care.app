package com.clinic.client.model;

import java.util.Set;

/** Singleton — stocke la session JWT en mémoire pendant l'exécution. */
public class AuthState {

    /**
     * Rôles autorisés sur le poste bureau (P6 — refonte rôles).
     * <p>
     * Le client lourd est désormais le <b>cockpit du propriétaire</b> : seul le rôle
     * {@code OWNER} s'y connecte (pilotage business, sans données de santé). Tout le
     * personnel clinique (médecins, infirmiers) et opérationnel (secrétaire, pharmacien,
     * caissier, laborantin) travaille sur l'application web.
     */
    public static final Set<String> DESKTOP_ROLES = Set.of("OWNER");

    private static AuthState instance;
    private String token;
    private String refreshToken;
    private long userId;
    private String username;
    private String role;
    private String fullName;

    private AuthState() {}

    /** Ce rôle a-t-il sa place sur le poste de soin ? */
    public static boolean isDesktopRole(String role) {
        return role != null && DESKTOP_ROLES.contains(role);
    }

    /** Libellé lisible d'un rôle, pour l'affichage. */
    public static String roleLabel(String role) {
        if (role == null) return "";
        return switch (role) {
            case "OWNER"      -> "Propriétaire";
            case "MEDECIN"    -> "Médecin";
            case "INFIRMIER"  -> "Infirmier";
            case "ADMIN"      -> "Administrateur";
            case "SECRETAIRE" -> "Secrétaire";
            case "PHARMACIEN" -> "Pharmacien";
            case "LABORANTIN" -> "Laborantin";
            case "CAISSIER"   -> "Caissier";
            case "PATIENT"    -> "Patient";
            default            -> role;
        };
    }

    public static AuthState get() {
        if (instance == null) instance = new AuthState();
        return instance;
    }

    public void login(String token, String refreshToken, long userId, String username, String role, String fullName) {
        this.token = token; this.refreshToken = refreshToken;
        this.userId = userId;
        this.username = username;
        this.role = role;   this.fullName = fullName;
    }

    /**
     * Met à jour le couple de jetons après une rotation via /api/auth/refresh.
     * Le refresh token est rotatif : on doit conserver le nouveau, l'ancien étant
     * révoqué côté serveur (le rejouer déclencherait la détection de réutilisation).
     */
    public void updateTokens(String token, String refreshToken) {
        this.token = token;
        this.refreshToken = refreshToken;
    }

    public void logout() {
        token = null; refreshToken = null;
        userId = 0;
        username = null; role = null; fullName = null;
    }

    public String getToken()        { return token; }
    public String getRefreshToken() { return refreshToken; }
    public long getUserId()         { return userId; }
    public String getUsername()     { return username; }
    public String getRole()         { return role; }
    public String getFullName()     { return fullName; }
    public boolean isLoggedIn()     { return token != null; }
}
