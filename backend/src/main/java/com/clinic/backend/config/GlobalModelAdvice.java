package com.clinic.backend.config;

import com.clinic.backend.model.User;
import com.clinic.backend.notification.NotificationRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;
import java.util.Set;

/**
 * Expose les attributs Thymeleaf dont toutes les vues ont besoin :
 * <ul>
 *   <li>{@code currentUri}         – chemin courant (active nav highlight)</li>
 *   <li>{@code principalModules}   – modules de la section PRINCIPAL pour ce rôle</li>
 *   <li>{@code soinsModules}       – modules de la section SOINS</li>
 *   <li>{@code gestionModules}     – modules de la section GESTION</li>
 *   <li>{@code adminModules}       – modules de la section ADMIN (vide si non ADMIN)</li>
 *   <li>{@code unreadNotifications}– badge notif filtré par types pertinents du rôle</li>
 * </ul>
 *
 * Toute la logique de sidebar est ici — {@code base.html} itère simplement
 * sur ces listes sans aucun {@code sec:authorize} manuel.
 */
@ControllerAdvice(annotations = org.springframework.stereotype.Controller.class)
@RequiredArgsConstructor
public class GlobalModelAdvice {

    private final NotificationRepository notificationRepository;

    // ── URI courante ─────────────────────────────────────────────────────────────

    @ModelAttribute("currentUri")
    public String currentUri(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null ? uri : "";
    }

    // ── Page d'accueil du rôle (ex. bouton « retour » des pages d'erreur) ────────

    @ModelAttribute("homeUrl")
    public String homeUrl() {
        String role = currentRole();
        return role == null ? "/login" : RoleProfile.fromRole(role).homepage;
    }

    // ── Module courant (fil d'Ariane, P6 WS5) ────────────────────────────────────
    // Le module dont l'URL de base est le plus long préfixe de l'URI courante
    // (ex. /consultations/5/prescription → CONSULTATIONS). null si aucun (ex. /profile).
    @ModelAttribute("currentModule")
    public Module currentModule(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) return null;
        Module best = null;
        for (Module m : Module.values()) {
            String p = m.urlPrefix;
            if (uri.equals(p) || uri.startsWith(p + "/")) {
                if (best == null || p.length() > best.urlPrefix.length()) best = m;
            }
        }
        return best;
    }

    // ── Sous-navigation du module courant (onglets, P6 WS5) ──────────────────────
    // Filtrés par rôle : un module hétérogène (Rapports) n'expose que les onglets
    // accessibles au rôle courant — jamais de destination en 403 dans la barre.
    @ModelAttribute("moduleTabs")
    public List<ModuleTabs.Tab> moduleTabs(HttpServletRequest request) {
        String role = currentRole();
        return ModuleTabs.forModule(currentModule(request)).stream()
                .filter(t -> t.visibleTo(role))
                .toList();
    }

    /** URL de l'onglet actif = le plus long préfixe de l'URI courante parmi les onglets. */
    @ModelAttribute("activeTabUrl")
    public String activeTabUrl(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) return null;
        String best = null;
        for (ModuleTabs.Tab t : moduleTabs(request)) {
            if (uri.equals(t.url()) || uri.startsWith(t.url() + "/")) {
                if (best == null || t.url().length() > best.length()) best = t.url();
            }
        }
        return best;
    }

    // ── Modules par section (zéro DB, tout en mémoire) ───────────────────────────

    @ModelAttribute("principalModules")
    public List<Module> principalModules() {
        return modulesForSection(Module.Section.PRINCIPAL);
    }

    @ModelAttribute("soinsModules")
    public List<Module> soinsModules() {
        return modulesForSection(Module.Section.SOINS);
    }

    @ModelAttribute("gestionModules")
    public List<Module> gestionModules() {
        return modulesForSection(Module.Section.GESTION);
    }

    @ModelAttribute("adminModules")
    public List<Module> adminModules() {
        return modulesForSection(Module.Section.ADMIN);
    }

    // ── Badge notifications (1 DB query filtrée par rôle) ────────────────────────

    @ModelAttribute("unreadNotifications")
    public long unreadNotifications() {
        User user = currentUser();
        if (user == null) return 0;
        Set<String> types = RoleProfile.fromRole(user.getRole()).notificationTypes;
        if (types.isEmpty()) return 0;
        return notificationRepository.countUnreadForUserAndTypes(user.getId(), types);
    }

    // ── Helpers privés ───────────────────────────────────────────────────────────

    private List<Module> modulesForSection(Module.Section section) {
        String role = currentRole();
        if (role == null) return List.of();
        return RoleProfile.fromRole(role).modulesForSection(section);
    }

    /** Rôle de l'utilisateur courant (ex. "PHARMACIEN"), null si non authentifié. */
    private String currentRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || "anonymousUser".equals(auth.getPrincipal())) return null;
        return auth.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse(null);
    }

    /** Utilisateur courant depuis le principal Spring Security (null si anonyme). */
    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object principal = auth.getPrincipal();
        return principal instanceof User u ? u : null;
    }
}
