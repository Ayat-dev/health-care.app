package com.clinic.backend.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertisseur JPA chiffrant les colonnes PHI sensibles au repos (P2.5).
 * À poser explicitement via {@code @Convert(converter = PhiStringConverter.class)}
 * (pas d'{@code autoApply} — on ne chiffre que les champs choisis, jamais les
 * colonnes recherchées comme nom/téléphone/n° dossier).
 *
 * <p>Hibernate instancie ce convertisseur lui-même (pas via Spring), d'où la
 * référence statique vers le {@link AesGcmCipher} injectée au démarrage par
 * {@link CryptoConfig}. Le chiffré n'est lu/écrit qu'au runtime (requêtes), bien
 * après que le contexte Spring ait câblé le cipher.
 */
@Converter
public class PhiStringConverter implements AttributeConverter<String, String> {

    private static volatile AesGcmCipher cipher;

    static void setCipher(AesGcmCipher cipher) {
        PhiStringConverter.cipher = cipher;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return cipher().encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return cipher().decrypt(dbData);
    }

    private static AesGcmCipher cipher() {
        AesGcmCipher c = cipher;
        if (c == null) {
            throw new IllegalStateException(
                    "AesGcmCipher non initialisé — CryptoConfig doit s'exécuter avant tout accès PHI.");
        }
        return c;
    }
}
