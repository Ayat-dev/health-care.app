package com.clinic.backend.security.mfa;

import com.clinic.backend.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Porte MFA (Tier E3) : après un login réussi (mot de passe), tant que le second facteur n'a pas
 * été validé <b>pour la session courante</b>, tout accès web d'un utilisateur MFA-activé est
 * redirigé vers {@code /mfa/challenge}. La protection contre la fixation de session (nouvelle
 * session à chaque login) garantit un nouveau challenge à chaque connexion.
 * <p>
 * Opt-in : sans effet pour les utilisateurs qui n'ont pas activé le MFA.
 */
public class MfaGuardInterceptor implements HandlerInterceptor {

    /** Attribut de session posé après validation du second facteur. */
    public static final String MFA_VERIFIED = "MFA_VERIFIED";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return true;
        if (!(auth.getPrincipal() instanceof User user) || !user.isMfaEnabled()) return true;

        HttpSession session = request.getSession(false);
        if (session != null && Boolean.TRUE.equals(session.getAttribute(MFA_VERIFIED))) return true;

        response.sendRedirect(request.getContextPath() + "/mfa/challenge");
        return false;
    }
}
