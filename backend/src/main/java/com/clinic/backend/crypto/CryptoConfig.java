package com.clinic.backend.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Câble le {@link AesGcmCipher} à partir du secret {@code app.encryption.key} et
 * l'injecte dans le {@link PhiStringConverter} (instancié par Hibernate, hors Spring).
 *
 * <p>Le secret n'a aucune valeur par défaut dans le profil prod → l'application
 * refuse de démarrer si {@code APP_ENCRYPTION_KEY} est absent (fail-fast, même
 * politique que le secret JWT). En dev/test une valeur fixe est fournie par les
 * {@code application-{dev,test}.properties}.
 */
@Configuration
public class CryptoConfig {

    @Bean
    public AesGcmCipher aesGcmCipher(@Value("${app.encryption.key}") String secret) {
        AesGcmCipher cipher = new AesGcmCipher(secret);
        PhiStringConverter.setCipher(cipher);
        return cipher;
    }
}
