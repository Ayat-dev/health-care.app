package com.clinic.backend.config;

import com.clinic.backend.realtime.WorklistChannels;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.web.csrf.XorCsrfChannelInterceptor;

/**
 * Autorisation des messages STOMP par rôle (P5.1 Lot D). Chaque worklist n'est abonnable que
 * par le service concerné (+ ADMIN). {@code @EnableWebSocketSecurity} câble aussi la protection
 * CSRF sur le CONNECT (le client web envoie son jeton CSRF en en-tête de connexion).
 */
@Configuration
@EnableWebSocketSecurity
public class WebSocketSecurityConfig {

    @Bean
    AuthorizationManager<Message<?>> messageAuthorizationManager(
            MessageMatcherDelegatingAuthorizationManager.Builder messages) {
        messages
                // CONNECT / DISCONNECT / UNSUBSCRIBE (destination nulle) : juste être authentifié.
                .nullDestMatcher().authenticated()
                .simpSubscribeDestMatchers(WorklistChannels.LAB).hasAnyRole("LABORANTIN", "ADMIN")
                .simpSubscribeDestMatchers(WorklistChannels.RADIOLOGY).hasAnyRole("MEDECIN", "ADMIN")
                .simpSubscribeDestMatchers(WorklistChannels.PHARMACY).hasAnyRole("PHARMACIEN", "ADMIN")
                .simpSubscribeDestMatchers(WorklistChannels.BILLING_QUEUE).hasAnyRole("CAISSIER", "ADMIN")
                // Tout le reste (envoi vers /app, autres topics) : refusé — ces worklists sont en
                // pur push descendant, les clients ne publient rien.
                .anyMessage().denyAll();
        return messages.build();
    }

    /**
     * Protection CSRF du CONNECT — adaptée aux deux voies d'authentification (P5.1 Lot E).
     *
     * <p>{@code @EnableWebSocketSecurity} récupère un bean nommé {@code csrfChannelInterceptor}
     * s'il existe (sinon il installe par défaut {@link XorCsrfChannelInterceptor}). On le
     * remplace ici par un délégué qui :
     * <ul>
     *   <li><b>laisse passer</b> les CONNECT stateless porteurs d'un en-tête
     *       {@code Authorization: Bearer} (client desktop JWT — pas de session, donc aucun jeton
     *       CSRF possible ; l'authentification se fait par le bearer, non forgeable cross-site) ;</li>
     *   <li><b>délègue au contrôle CSRF standard</b> ({@link XorCsrfChannelInterceptor}) pour tous
     *       les autres messages — la protection du client web (session + cookie) reste intacte.</li>
     * </ul>
     */
    @Bean
    ChannelInterceptor csrfChannelInterceptor() {
        XorCsrfChannelInterceptor delegate = new XorCsrfChannelInterceptor();
        return new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null
                        && StompCommand.CONNECT.equals(accessor.getCommand())
                        && isBearer(accessor.getFirstNativeHeader("Authorization"))) {
                    return message; // connexion stateless JWT : CSRF sans objet
                }
                return delegate.preSend(message, channel);
            }
        };
    }

    private static boolean isBearer(String header) {
        return header != null && header.startsWith("Bearer ");
    }
}
