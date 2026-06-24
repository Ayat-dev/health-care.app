package com.clinic.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Infrastructure temps réel STOMP (P5.1 Lot D) : broker en mémoire {@code /topic}, préfixe
 * applicatif {@code /app}, endpoint de handshake {@code /ws}.
 *
 * <p><b>Endpoint brut (sans SockJS)</b> : le client web est un petit client STOMP sur
 * {@code WebSocket} natif (cf. {@code static/js/worklist-live.js}). La CSP du projet
 * ({@code default-src 'self'}, aucun CDN) proscrit le chargement de SockJS/stomp.js externes ;
 * un endpoint brut évite de vendoriser ces libs. Les origines restent <b>same-origin</b> par
 * défaut (aucun {@code setAllowedOrigins}) et le handshake passe par la chaîne de sécurité web
 * (session obligatoire).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws");
    }
}
