package com.clinic.client.model;

/** Singleton — stocke la session JWT en mémoire pendant l'exécution. */
public class AuthState {
    private static AuthState instance;
    private String token;
    private String username;
    private String role;
    private String fullName;

    private AuthState() {}

    public static AuthState get() {
        if (instance == null) instance = new AuthState();
        return instance;
    }

    public void login(String token, String username, String role, String fullName) {
        this.token = token; this.username = username;
        this.role = role;   this.fullName = fullName;
    }

    public void logout() { token = null; username = null; role = null; fullName = null; }

    public String getToken()    { return token; }
    public String getUsername() { return username; }
    public String getRole()     { return role; }
    public String getFullName() { return fullName; }
    public boolean isLoggedIn() { return token != null; }
}
