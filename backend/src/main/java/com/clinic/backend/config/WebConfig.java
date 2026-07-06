package com.clinic.backend.config;

import com.clinic.backend.license.LicenseGuardInterceptor;
import com.clinic.backend.license.LicenseService;
import com.clinic.backend.setup.SetupGuardInterceptor;
import com.clinic.backend.setup.SetupService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Configuration MVC :
 * - Expose le répertoire uploads sous /uploads/**
 * - Configure CORS pour l'API REST (/api/**)
 * - i18n (P3.2) : résolveur de locale par cookie + bascule via ?lang=fr|en|ar
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final SetupService setupService;
    private final LicenseService licenseService;

    public WebConfig(SetupService setupService, LicenseService licenseService) {
        this.setupService = setupService;
        this.licenseService = licenseService;
    }

    // ─── Fichiers uploadés (photos patients, images radiology) ───────────────
    // D3b : plus de handler de ressources statiques pour /uploads/** — les fichiers
    // sont chiffrés au repos, donc servis (déchiffrés) par UploadedFileController.

    // ─── i18n : locale persistée en cookie, défaut français (P3.2) ───────────
    // La locale survit à la déconnexion et au redémarrage navigateur (cookie 1 an).
    @Bean
    public LocaleResolver localeResolver() {
        CookieLocaleResolver resolver = new CookieLocaleResolver("clinicLang");
        resolver.setDefaultLocale(Locale.FRENCH);
        resolver.setCookieMaxAge(Duration.ofDays(365));
        resolver.setCookiePath("/");
        return resolver;
    }

    // Bascule de langue via ?lang=fr|en|ar sur n'importe quelle page (y compris /login).
    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        interceptor.setIgnoreInvalidLocale(true);
        return interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());

        // Première installation (cf. SetupGuardInterceptor) : redirige tout le web
        // vers /setup tant qu'aucun utilisateur n'existe. On exclut l'assistant
        // lui-même, les statiques, /error et tous les endpoints machine (API, FHIR,
        // WebSocket, Actuator, console H2) — qui ne doivent pas recevoir de 302 HTML.
        registry.addInterceptor(new SetupGuardInterceptor(setupService))
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/setup", "/setup/**",
                        "/css/**", "/js/**", "/images/**", "/uploads/**", "/favicon.ico",
                        "/manifest.webmanifest", "/sw.js", "/offline.html",
                        "/error",
                        "/api/**", "/fhir/**", "/ws/**", "/actuator/**", "/h2-console/**");

        // Porte MFA (Tier E3) : force le second facteur après login pour les comptes MFA-activés.
        // On laisse passer le challenge lui-même, le logout, le login, l'erreur, les statiques et
        // les endpoints machine (qui ne doivent pas recevoir de 302 HTML).
        registry.addInterceptor(new com.clinic.backend.security.mfa.MfaGuardInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/mfa/challenge",
                        "/login", "/logout", "/auth/**",
                        "/setup", "/setup/**",
                        "/css/**", "/js/**", "/images/**", "/uploads/**", "/favicon.ico",
                        "/manifest.webmanifest", "/sw.js", "/offline.html",
                        "/error",
                        "/api/**", "/fhir/**", "/ws/**", "/actuator/**", "/h2-console/**");

        // Expiration de licence (Phase 3 desktop) : en état bloqué, l'app passe en
        // lecture seule (seules les écritures POST/PUT/PATCH/DELETE sont refusées et
        // redirigées vers /license). Lectures + export (GET) toujours ouverts. On exclut
        // l'activation, l'auth, le MFA, les statiques et les endpoints machine.
        registry.addInterceptor(new LicenseGuardInterceptor(licenseService))
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/license", "/license/**",
                        "/login", "/logout", "/auth/**",
                        "/mfa/**",
                        "/setup", "/setup/**",
                        "/css/**", "/js/**", "/images/**", "/uploads/**", "/favicon.ico",
                        "/manifest.webmanifest", "/sw.js", "/offline.html",
                        "/error",
                        "/api/**", "/fhir/**", "/ws/**", "/actuator/**", "/h2-console/**");
    }

    // ─── CORS — API uniquement (/api/**) ────────────────────────────────────
    // En dev : localhost:8080 (Thymeleaf) + localhost:8888 (JavaFX)
    // En prod : définir app.cors.allowed-origins dans application-prod.properties
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "http://localhost:8080",
                "http://localhost:8888"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);

        return new CorsFilter(source);
    }
}
