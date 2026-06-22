package com.clinic.backend.tenant;

import com.clinic.backend.model.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.function.Supplier;

/**
 * Multi-tenant (P4.2) — résolution de la clinique courante pour la requête en cours.
 * <p>
 * Deux sources, dans l'ordre :
 * <ol>
 *   <li>un <b>override</b> explicite posé sur le thread ({@link #runAs}/{@link #set}) —
 *       utilisé hors contexte de requête HTTP : amorçage des données, webhooks Mobile
 *       Money (pas d'utilisateur authentifié), tests ;</li>
 *   <li>la clinique de l'<b>utilisateur authentifié</b> ({@link User#getClinicId()}).</li>
 * </ol>
 * Renvoie {@code null} si aucune des deux n'est disponible (ex. SUPER_ADMIN transverse,
 * ou tâche de fond sans contexte) — {@link ClinicTenantResolver} traduit ce {@code null}
 * en un tenant sentinelle « fermé » (aucune donnée visible).
 */
public final class TenantContext {

    private static final ThreadLocal<Long> OVERRIDE = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(Long clinicId) { OVERRIDE.set(clinicId); }

    public static Long getOverride() { return OVERRIDE.get(); }

    public static void clear() { OVERRIDE.remove(); }

    /** Clinique de la requête : override explicite, sinon utilisateur authentifié, sinon {@code null}. */
    public static Long currentClinicId() {
        Long override = OVERRIDE.get();
        if (override != null) return override;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return user.getClinicId();
        }
        return null;
    }

    /** Exécute {@code body} en forçant la clinique {@code clinicId} (restaure l'état précédent ensuite). */
    public static void runAs(Long clinicId, Runnable body) {
        Long previous = OVERRIDE.get();
        OVERRIDE.set(clinicId);
        try {
            body.run();
        } finally {
            if (previous != null) OVERRIDE.set(previous); else OVERRIDE.remove();
        }
    }

    /** Variante de {@link #runAs} renvoyant une valeur. */
    public static <T> T callAs(Long clinicId, Supplier<T> body) {
        Long previous = OVERRIDE.get();
        OVERRIDE.set(clinicId);
        try {
            return body.get();
        } finally {
            if (previous != null) OVERRIDE.set(previous); else OVERRIDE.remove();
        }
    }
}
