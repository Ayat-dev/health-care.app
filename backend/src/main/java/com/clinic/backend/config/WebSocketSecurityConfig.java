package com.clinic.backend.config;

import com.clinic.backend.realtime.WorklistChannels;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;

/**
 * Autorisation des messages STOMP par rôle (P5.1 Lot D). Chaque worklist n'est abonnable que
 * par le service concerné (+ ADMIN). {@code @EnableWebSocketSecurity} câble aussi la protection
 * CSRF sur le CONNECT (le client envoie le jeton {@code X-CSRF-TOKEN} en en-tête de connexion).
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
}
