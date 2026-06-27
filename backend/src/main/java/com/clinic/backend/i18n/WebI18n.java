package com.clinic.backend.i18n;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * Helper i18n unique pour le code Java (flash messages des contrôleurs web, etc.).
 * <p>
 * Résout une clé du bundle {@code messages*.properties} dans la locale courante
 * (cookie {@code clinicLang}, cf. {@code WebConfig}). Évite de répéter
 * {@code messageSource.getMessage(key, args, LocaleContextHolder.getLocale())}
 * partout. Convention des clés : {@code module.flash.*} (cf. docs/I18N-PLAN.md §1).
 *
 * <pre>{@code
 *   ra.addFlashAttribute("success", i18n.t("patients.flash.created"));
 *   ra.addFlashAttribute("error",   i18n.t("billing.flash.overpay", balance));
 * }</pre>
 */
@Component
public class WebI18n {

    private final MessageSource messageSource;

    public WebI18n(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /** Libellé de {@code key} dans la locale courante ; {@code args} pour les placeholders {0},{1}… */
    public String t(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }

    /** Variante tolérante : renvoie {@code defaultMessage} (au lieu de lever) si la clé manque. */
    public String tOrDefault(String key, String defaultMessage, Object... args) {
        return messageSource.getMessage(key, args, defaultMessage, LocaleContextHolder.getLocale());
    }
}
