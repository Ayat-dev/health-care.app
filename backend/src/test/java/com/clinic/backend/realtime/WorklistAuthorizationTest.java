package com.clinic.backend.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gating par rôle des abonnements STOMP (P5.1 Lot D) : on exerce directement
 * l'{@code AuthorizationManager<Message<?>>} câblé par {@code @EnableWebSocketSecurity}
 * sur des messages SUBSCRIBE simulés — pas besoin d'un vrai socket. Chaque worklist n'est
 * abonnable que par son service (+ ADMIN).
 */
@SpringBootTest
@ActiveProfiles("test")
class WorklistAuthorizationTest {

    @Autowired
    @Qualifier("messageAuthorizationManager")
    AuthorizationManager<Message<?>> authz;

    private static Message<?> subscribe(String destination) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.SUBSCRIBE);
        accessor.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static Supplier<Authentication> user(String... roles) {
        List<SimpleGrantedAuthority> auths = Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList();
        Authentication auth = new UsernamePasswordAuthenticationToken("u", "p", auths);
        return () -> auth;
    }

    private boolean granted(Supplier<Authentication> who, String destination) {
        var decision = authz.check(who, subscribe(destination));
        return decision != null && decision.isGranted();
    }

    @Test
    void chaque_service_voit_sa_worklist() {
        assertThat(granted(user("ROLE_LABORANTIN"), WorklistChannels.LAB)).isTrue();
        assertThat(granted(user("ROLE_MEDECIN"), WorklistChannels.RADIOLOGY)).isTrue();
        assertThat(granted(user("ROLE_PHARMACIEN"), WorklistChannels.PHARMACY)).isTrue();
        assertThat(granted(user("ROLE_CAISSIER"), WorklistChannels.BILLING_QUEUE)).isTrue();
    }

    @Test
    void un_role_etranger_est_refuse() {
        assertThat(granted(user("ROLE_CAISSIER"), WorklistChannels.LAB)).isFalse();
        assertThat(granted(user("ROLE_LABORANTIN"), WorklistChannels.BILLING_QUEUE)).isFalse();
        assertThat(granted(user("ROLE_PHARMACIEN"), WorklistChannels.RADIOLOGY)).isFalse();
    }

    @Test
    void l_admin_voit_toutes_les_worklists() {
        Supplier<Authentication> admin = user("ROLE_ADMIN");
        assertThat(granted(admin, WorklistChannels.LAB)).isTrue();
        assertThat(granted(admin, WorklistChannels.RADIOLOGY)).isTrue();
        assertThat(granted(admin, WorklistChannels.PHARMACY)).isTrue();
        assertThat(granted(admin, WorklistChannels.BILLING_QUEUE)).isTrue();
    }
}
