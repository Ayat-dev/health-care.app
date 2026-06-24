package com.clinic.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.simp.config.ChannelRegistration;
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
 * défaut (aucun {@code setAllowedOrigins}).
 *
 * <p><b>Deux voies d'authentification au handshake/CONNECT</b> :
 * <ul>
 *   <li><b>Web</b> : poignée de main authentifiée par la session (chaîne web) + jeton CSRF dans
 *       la trame CONNECT.</li>
 *   <li><b>Poste de soin desktop (P5.1 Lot E)</b> : stateless. La poignée de main {@code /ws} est
 *       ouverte (permitAll côté HTTP) et l'authentification a lieu au CONNECT via le jeton JWT
 *       porté en en-tête {@code Authorization} ({@link StompAuthChannelInterceptor}).</li>
 * </ul>
 *
 * <p>{@code @Order(HIGHEST_PRECEDENCE)} : ce configurer doit enregistrer son intercepteur
 * d'authentification JWT <b>avant</b> les intercepteurs de sécurité ajoutés par
 * {@code @EnableWebSocketSecurity} (contexte + CSRF + autorisation), pour que l'utilisateur JWT
 * soit fixé avant tout contrôle d'accès.
 */
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Ajouté en premier (précédence haute de ce configurer) → s'exécute avant les
        // intercepteurs de @EnableWebSocketSecurity, de sorte que le Principal JWT du client
        // desktop soit posé avant le contrôle d'autorisation des abonnements.
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
