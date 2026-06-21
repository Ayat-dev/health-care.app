package com.clinic.backend.scribe;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Câble le client Anthropic du scribe IA (P4.1) à partir de {@code app.scribe.api-key}.
 *
 * <p>Le bean n'est créé que lorsque {@code app.scribe.enabled=true} : en dev/test
 * la fonctionnalité est désactivée par défaut, donc aucune clé n'est requise et
 * l'application démarre normalement. Lorsqu'on l'active sans fournir de clé,
 * l'application <em>refuse de démarrer</em> (fail-fast, même politique que les
 * secrets JWT / chiffrement PHI).
 */
@Configuration
public class ScribeConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.scribe", name = "enabled", havingValue = "true")
    public AnthropicClient anthropicClient(@Value("${app.scribe.api-key:}") String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException(
                "app.scribe.enabled=true mais aucune clé API Anthropic fournie " +
                "(définir ANTHROPIC_API_KEY ou app.scribe.api-key).");
        }
        return AnthropicOkHttpClient.builder().apiKey(apiKey).build();
    }
}
