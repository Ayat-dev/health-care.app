package com.clinic.backend.config;

import com.clinic.backend.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Authentification JWT du CONNECT STOMP du poste de soin desktop (P5.1 Lot E).
 *
 * <p>On exerce directement les deux intercepteurs du canal entrant sur des trames CONNECT
 * simulées (pas de vrai socket) : l'auth par bearer ({@link StompAuthChannelInterceptor}) et la
 * protection CSRF adaptée ({@code csrfChannelInterceptor} de {@link WebSocketSecurityConfig}).
 */
@SpringBootTest
@ActiveProfiles("test")
class StompAuthChannelInterceptorTest {

    @Autowired StompAuthChannelInterceptor authInterceptor;
    @Autowired @Qualifier("csrfChannelInterceptor") ChannelInterceptor csrfInterceptor;
    @Autowired JwtService jwtService;

    private static Message<byte[]> connect(String authorizationHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        if (authorizationHeader != null) {
            accessor.addNativeHeader("Authorization", authorizationHeader);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void un_connect_bearer_valide_authentifie_l_utilisateur() {
        String token = jwtService.generateToken("dr.martin", "MEDECIN", 0);

        Message<?> out = authInterceptor.preSend(connect("Bearer " + token), null);

        var accessor = MessageHeaderAccessor.getAccessor(out, StompHeaderAccessor.class);
        assertThat(accessor.getUser()).isNotNull();
        assertThat(accessor.getUser().getName()).isEqualTo("dr.martin");
    }

    @Test
    void un_connect_sans_bearer_n_authentifie_personne() {
        Message<?> out = authInterceptor.preSend(connect(null), null);

        var accessor = MessageHeaderAccessor.getAccessor(out, StompHeaderAccessor.class);
        assertThat(accessor.getUser()).isNull();
    }

    @Test
    void un_jeton_invalide_n_authentifie_personne() {
        Message<?> out = authInterceptor.preSend(connect("Bearer pas-un-vrai-jwt"), null);

        var accessor = MessageHeaderAccessor.getAccessor(out, StompHeaderAccessor.class);
        assertThat(accessor.getUser()).isNull();
    }

    @Test
    void le_csrf_laisse_passer_un_connect_bearer_stateless() {
        String token = jwtService.generateToken("dr.martin", "MEDECIN", 0);

        assertThatCode(() -> csrfInterceptor.preSend(connect("Bearer " + token), null))
                .doesNotThrowAnyException();
    }

    @Test
    void le_csrf_protege_toujours_le_connect_web_sans_jeton() {
        // CONNECT web simulé (ni bearer, ni session, ni jeton CSRF) → toujours refusé.
        assertThatThrownBy(() -> csrfInterceptor.preSend(connect(null), null))
                .isInstanceOf(CsrfException.class);
    }
}
