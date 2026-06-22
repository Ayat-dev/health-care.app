package com.clinic.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.header.writers.frameoptions.XFrameOptionsHeaderWriter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final RoleAuthenticationSuccessHandler successHandler;
    private final LoginFailureHandler failureHandler;

    public SecurityConfig(JwtFilter jwtFilter,
                          RoleAuthenticationSuccessHandler successHandler,
                          LoginFailureHandler failureHandler) {
        this.jwtFilter      = jwtFilter;
        this.successHandler = successHandler;
        this.failureHandler = failureHandler;
    }

    // ─── Chaîne 0 : Actuator — monitoring (P4.3) ────────────────────────────
    // health + info publics (sondes UptimeRobot / load-balancer, anonymes) ;
    // prometheus + metrics + tout le reste sous HTTP Basic avec un compte de
    // scraping DÉDIÉ EN MÉMOIRE, découplé des utilisateurs métier (rotation
    // indépendante, aucune dépendance à la base). Mot de passe fail-fast en prod.
    @Bean
    @Order(0)
    public SecurityFilterChain actuatorFilterChain(
            HttpSecurity http,
            PasswordEncoder passwordEncoder,
            @Value("${app.monitoring.username}") String monitoringUsername,
            @Value("${app.monitoring.password}") String monitoringPassword) throws Exception {

        UserDetails monitor = User.withUsername(monitoringUsername)
                .password(passwordEncoder.encode(monitoringPassword))
                .authorities("ENDPOINT_ADMIN")
                .build();
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(new InMemoryUserDetailsManager(monitor));
        provider.setPasswordEncoder(passwordEncoder);

        http
            // Matcher littéral (et non EndpointRequest.toAnyEndpoint()) : capte TOUT
            // /actuator/** quel que soit l'enregistrement des endpoints → pas de
            // retombée vers la chaîne web (302 login) pour prometheus & co.
            .securityMatcher("/actuator/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authenticationManager(new ProviderManager(provider))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(EndpointRequest.to(HealthEndpoint.class, InfoEndpoint.class)).permitAll()
                .anyRequest().hasAuthority("ENDPOINT_ADMIN")
            )
            .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    // ─── Chaîne 1 : API REST — stateless, JWT ───────────────────────────────
    @Bean
    @Order(1)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
            // /fhir/** = API d'interopérabilité FHIR R4 (P2.1) : même modèle stateless/JWT que /api/**.
            .securityMatcher("/api/**", "/fhir/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers
                .addHeaderWriter(new StaticHeadersWriter("X-Content-Type-Options", "nosniff"))
                .addHeaderWriter(new StaticHeadersWriter("X-Frame-Options", "DENY"))
                .addHeaderWriter(new StaticHeadersWriter("Referrer-Policy", "strict-origin-when-cross-origin"))
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login").permitAll()
                // P4.4 : refresh/logout appelables sans access token valide (il peut être
                // expiré) — le refresh token EST la pièce d'authentification. logout-all reste
                // authentifié (il agit sur l'utilisateur courant).
                .requestMatchers("/api/auth/refresh", "/api/auth/logout").permitAll()
                .requestMatchers("/fhir/metadata").permitAll()
                // Webhooks Mobile Money (P3.3) : authentifiés par signature HMAC, pas par JWT.
                .requestMatchers("/api/payments/webhook/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // ─── Chaîne 2 : Web Thymeleaf — session + form login ────────────────────
    @Bean
    @Order(2)
    public SecurityFilterChain webFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/h2-console/**")
            )
            .headers(headers -> headers
                // Autorise les iframes H2 console (SAMEORIGIN) et bloque les autres
                .addHeaderWriter(new XFrameOptionsHeaderWriter(
                    XFrameOptionsHeaderWriter.XFrameOptionsMode.SAMEORIGIN))
                .addHeaderWriter(new StaticHeadersWriter("X-Content-Type-Options", "nosniff"))
                .addHeaderWriter(new StaticHeadersWriter("Referrer-Policy", "strict-origin-when-cross-origin"))
                // CSP : autorise uniquement les ressources du même domaine
                .addHeaderWriter(new StaticHeadersWriter(
                    "Content-Security-Policy",
                    "default-src 'self'; " +
                    "script-src 'self' 'unsafe-inline'; " +
                    "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
                    "font-src 'self' https://fonts.gstatic.com; " +
                    "img-src 'self' data: blob:; " +
                    "manifest-src 'self'; " +
                    "worker-src 'self'; " +
                    "connect-src 'self';"
                ))
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/login", "/auth/**",
                    "/error",
                    "/h2-console/**",
                    "/css/**", "/js/**", "/images/**", "/uploads/**", "/favicon.ico",
                    // PWA (P3.1) — installables/utilisables sans session
                    "/manifest.webmanifest", "/sw.js", "/offline.html"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler(successHandler)
                .failureHandler(failureHandler)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .permitAll()
            )
            .exceptionHandling(ex -> ex
                .accessDeniedPage("/error")
            );

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Publie les événements succès/échec d'authentification consommés par
     * {@link AuthenticationEventListener} (anti-brute-force, P1.3). Sans ce bean
     * l'{@code AuthenticationManager} utilise un publisher no-op.
     */
    @Bean
    public AuthenticationEventPublisher authenticationEventPublisher(ApplicationEventPublisher delegate) {
        return new DefaultAuthenticationEventPublisher(delegate);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
