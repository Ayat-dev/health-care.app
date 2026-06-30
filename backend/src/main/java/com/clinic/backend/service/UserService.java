package com.clinic.backend.service;

import com.clinic.backend.config.ResourceNotFoundException;
import com.clinic.backend.dto.UserDto;
import com.clinic.backend.model.Role;
import com.clinic.backend.model.User;
import com.clinic.backend.repository.UserRepository;
import com.clinic.backend.security.RefreshTokenService;
import com.clinic.backend.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Business logic for admin user management: create, edit, enable/disable, soft delete.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    // ── Liste (non supprimés) ──────────────────────────────────────────────
    // Multi-tenant (P4.2) : un ADMIN de clinique ne gère que les comptes de SA clinique.
    // Le SUPER_ADMIN (clinique courante = null) voit tout. users n'est pas @TenantId
    // (la connexion par username doit rester globale) → filtrage applicatif explicite.
    @Transactional(readOnly = true)
    public List<User> listActive() {
        Long clinic = TenantContext.currentClinicId();
        return clinic == null
                ? userRepository.findByDeletedAtIsNullOrderByUsernameAsc()
                : userRepository.findByDeletedAtIsNullAndClinicIdOrderByUsernameAsc(clinic);
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable : " + id));
        if (u.getDeletedAt() != null)
            throw new IllegalArgumentException("Utilisateur supprimé : " + id);
        // Cloisonnement tenant : pas d'accès à un compte d'une autre clinique.
        Long clinic = TenantContext.currentClinicId();
        if (clinic != null && !clinic.equals(u.getClinicId()))
            throw new ResourceNotFoundException("Utilisateur introuvable : " + id);
        return u;
    }

    /** Rôles assignables (hors SUPER_ADMIN, transverse et non créable ici). */
    public List<Role> assignableRoles() {
        return Arrays.stream(Role.values()).filter(r -> r != Role.SUPER_ADMIN).toList();
    }

    // ── Création ────────────────────────────────────────────────────────────
    public User create(UserDto dto) {
        String username = dto.getUsername() == null ? "" : dto.getUsername().trim();
        if (username.isEmpty())
            throw new IllegalArgumentException("Le nom d'utilisateur est obligatoire.");
        if (userRepository.existsByUsername(username))
            throw new IllegalArgumentException("Ce nom d'utilisateur existe déjà : " + username);
        validateRole(dto.getRole());
        validatePassword(dto.getPassword());

        User u = new User(username, passwordEncoder.encode(dto.getPassword()),
                dto.getFullName(), dto.getRole());
        u.setActive(dto.isActive());
        // Le nouveau compte hérite de la clinique de l'admin créateur (multi-tenant P4.2).
        u.setClinicId(TenantContext.currentClinicId());
        User saved = userRepository.save(u);
        log.info("Utilisateur créé : {} (rôle {})", saved.getUsername(), saved.getRole());
        return saved;
    }

    /**
     * Provisionne le premier ADMIN d'une clinique (flux SUPER_ADMIN, multi-tenant P4.2).
     * Contrairement à {@link #create(UserDto)} (qui rattache à la clinique du créateur), la
     * clinique est passée explicitement — le SUPER_ADMIN n'a pas de clinique courante. Rôle forcé ADMIN.
     */
    public User createForClinic(Long clinicId, UserDto dto) {
        if (clinicId == null)
            throw new IllegalArgumentException("La clinique est obligatoire.");
        String username = dto.getUsername() == null ? "" : dto.getUsername().trim();
        if (username.isEmpty())
            throw new IllegalArgumentException("Le nom d'utilisateur est obligatoire.");
        if (userRepository.existsByUsername(username))
            throw new IllegalArgumentException("Ce nom d'utilisateur existe déjà : " + username);
        validatePassword(dto.getPassword());

        User u = new User(username, passwordEncoder.encode(dto.getPassword()),
                dto.getFullName(), Role.ADMIN.name());
        u.setActive(true);
        u.setClinicId(clinicId);
        User saved = userRepository.save(u);
        log.info("Admin de clinique provisionné : {} (clinique {})", saved.getUsername(), clinicId);
        return saved;
    }

    // ── Modification ──────────────────────────────────────────────────────────
    public User update(Long id, UserDto dto) {
        User u = getById(id);
        validateRole(dto.getRole());
        u.setFullName(dto.getFullName());
        u.setRole(dto.getRole());
        u.setActive(dto.isActive());
        // Mot de passe optionnel : laissé vide => inchangé
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            validatePassword(dto.getPassword());
            u.setPassword(passwordEncoder.encode(dto.getPassword()));
            // Révocation JWT (P4.4) : un reset de mot de passe coupe les sessions en cours.
            refreshTokenService.revokeAllForUser(u);
            log.info("Mot de passe réinitialisé pour {}", u.getUsername());
        } else if (!u.isActive()) {
            // Désactivé via l'édition → on coupe ses sessions.
            refreshTokenService.revokeAllForUser(u);
        }
        return userRepository.save(u);
    }

    // ── Changement de mot de passe en self-service (portail patient — D4b) ──────
    /**
     * Change le mot de passe de l'utilisateur courant après vérification du mot de
     * passe actuel. Coupe les autres sessions (révocation JWT + bump de version de
     * jeton). Utilisé par le portail patient ; réutilisable pour un futur /profile staff.
     *
     * @throws IllegalArgumentException si le mot de passe actuel est incorrect ou si
     *         le nouveau ne respecte pas la politique (≥ 8 caractères, ≥ 1 chiffre)
     */
    public void changeOwnPassword(Long userId, String currentPassword, String newPassword) {
        User u = getById(userId);
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, u.getPassword())) {
            throw new IllegalArgumentException("Mot de passe actuel incorrect.");
        }
        validatePassword(newPassword);
        u.setPassword(passwordEncoder.encode(newPassword));
        u.bumpTokenVersion();
        refreshTokenService.revokeAllForUser(u);
        log.info("Mot de passe changé en self-service pour {}", u.getUsername());
        userRepository.save(u);
    }

    // ── Activer / désactiver ──────────────────────────────────────────────────
    public void toggleActive(Long id) {
        User u = getById(id);
        u.setActive(!u.isActive());
        // Révocation JWT (P4.4) : une désactivation coupe immédiatement les sessions.
        if (!u.isActive()) refreshTokenService.revokeAllForUser(u);
        log.info("Utilisateur {} : actif = {}", u.getUsername(), u.isActive());
    }

    // ── Suppression logique ───────────────────────────────────────────────────
    public void delete(Long id) {
        User u = getById(id);
        u.setDeletedAt(LocalDateTime.now());
        u.setActive(false); // un compte supprimé ne peut plus se connecter
        // Révocation JWT (P4.4) : plus aucune session après suppression.
        refreshTokenService.revokeAllForUser(u);
        log.info("Utilisateur supprimé (logique) : {}", u.getUsername());
    }

    // ── Mapping entité → DTO ──────────────────────────────────────────────────
    public UserDto toDto(User u) {
        UserDto dto = new UserDto();
        dto.setId(u.getId());
        dto.setUsername(u.getUsername());
        dto.setFullName(u.getFullName());
        dto.setRole(u.getRole());
        dto.setActive(u.isActive());
        dto.setCreatedAt(u.getCreatedAt());
        return dto;
    }

    // ── Validation ────────────────────────────────────────────────────────────
    private void validateRole(String role) {
        // SUPER_ADMIN est transverse : non assignable via la gestion des utilisateurs (P4.2).
        if (role == null || Role.SUPER_ADMIN.name().equals(role)
                || Arrays.stream(Role.values()).noneMatch(r -> r.name().equals(role)))
            throw new IllegalArgumentException("Rôle invalide : " + role);
    }

    /** Règle métier : minimum 8 caractères, au moins 1 chiffre. */
    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || !password.matches(".*\\d.*"))
            throw new IllegalArgumentException(
                    "Le mot de passe doit contenir au moins 8 caractères et 1 chiffre.");
    }
}
