package com.clinic.backend.config;

import com.clinic.backend.model.User;
import com.clinic.backend.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

/**
 * Authentifie les clients STOMP par jeton JWT au CONNECT (P5.1 Lot E — poste de soin desktop).
 *
 * <p>Le client web s'authentifie déjà à la poignée de main via la session (chaîne web). Le
 * client lourd JavaFX, lui, est <b>stateless</b> (JWT) : il ne porte ni cookie de session ni
 * jeton CSRF. Il présente donc son access token dans l'en-tête {@code Authorization: Bearer}
 * de la trame CONNECT ; cet intercepteur le valide (mêmes règles que
 * {@link com.clinic.backend.security.JwtFilter} — actif, non verrouillé, version de jeton à
 * jour) et fixe l'utilisateur de la session WebSocket. Les abonnements suivants héritent de ce
 * {@code Principal} et sont filtrés par rôle par l'{@code AuthorizationChannelInterceptor}
 * (cf. {@link WebSocketSecurityConfig}).
 *
 * <p>Sans en-tête {@code Authorization} (cas du web), l'intercepteur ne fait rien : le
 * {@code Principal} issu de la session de la poignée de main reste en place. Cet intercepteur
 * doit précéder les intercepteurs de sécurité — l'ordre est garanti par la précédence haute de
 * {@link WebSocketConfig} parmi les configurers.
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return message; // client web (session) : rien à faire
        }

        String token = header.substring(7);
        if (!jwtService.isTokenValid(token)) {
            return message; // jeton invalide → CONNECT refusé par l'autorisation (non authentifié)
        }

        String username = jwtService.extractUsername(token);
        if (username == null) {
            return message;
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        if (authenticatable(userDetails, jwtService.extractTokenVersion(token))) {
            var auth = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            accessor.setUser(auth);
        }
        return message;
    }

    /** Mêmes garde-fous de révocation immédiate que {@code JwtFilter} (P4.4). */
    private boolean authenticatable(UserDetails userDetails, int tokenVersion) {
        if (!userDetails.isEnabled() || !userDetails.isAccountNonLocked()) return false;
        return !(userDetails instanceof User user) || user.getTokenVersion() == tokenVersion;
    }
}
