package com.clinic.backend.setup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Garde d'installation : redirige vers {@code /setup} tant que l'application n'est pas
 * installée, laisse passer ensuite.
 */
@ExtendWith(MockitoExtension.class)
class SetupGuardInterceptorTest {

    @Mock SetupService setupService;

    @Test
    void redirige_vers_setup_quand_non_installe() {
        when(setupService.isSetupRequired()).thenReturn(true);
        SetupGuardInterceptor interceptor = new SetupGuardInterceptor(setupService);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/patients");
        MockHttpServletResponse res = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(req, res, new Object());

        assertThat(proceed).isFalse();
        assertThat(res.getStatus()).isEqualTo(302);
        assertThat(res.getHeader("Location")).isEqualTo("/setup");
    }

    @Test
    void laisse_passer_quand_installe() {
        when(setupService.isSetupRequired()).thenReturn(false);
        SetupGuardInterceptor interceptor = new SetupGuardInterceptor(setupService);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/patients");
        MockHttpServletResponse res = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(req, res, new Object());

        assertThat(proceed).isTrue();
        assertThat(res.getStatus()).isEqualTo(200); // statut par défaut, non modifié
    }
}
