package com.clinic.backend.i18n;

import com.clinic.backend.billing.PaymentMethods;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice 0 i18n (docs/I18N-PLAN.md) : le socle transverse (common/status/priority/paymethod)
 * se résout dans les 3 langues. Filet anti-oubli : une clé manquante dans un bundle lèverait
 * {@code NoSuchMessageException} ici plutôt qu'un 500 à l'exécution.
 */
@SpringBootTest
@ActiveProfiles("test")
class I18nBundleTest {

    @Autowired MessageSource messages;
    @Autowired PaymentMethods paymentMethods;

    private static final Locale AR = Locale.forLanguageTag("ar");

    @Test
    void status_se_resout_dans_les_trois_langues() {
        assertThat(messages.getMessage("status.EN_ATTENTE", null, Locale.FRENCH)).isEqualTo("En attente");
        assertThat(messages.getMessage("status.EN_ATTENTE", null, Locale.ENGLISH)).isEqualTo("Pending");
        assertThat(messages.getMessage("status.EN_ATTENTE", null, AR)).isEqualTo("قيد الانتظار");

        assertThat(messages.getMessage("status.VALIDE", null, Locale.ENGLISH)).isEqualTo("Validated");
        assertThat(messages.getMessage("status.ADMIS", null, Locale.FRENCH)).isEqualTo("Admis");
    }

    @Test
    void priority_et_paymethod_se_resolvent() {
        assertThat(messages.getMessage("priority.URGENT", null, Locale.ENGLISH)).isEqualTo("Urgent");
        assertThat(messages.getMessage("paymethod.ESPECES", null, Locale.FRENCH)).isEqualTo("Espèces");
        assertThat(messages.getMessage("paymethod.ESPECES", null, Locale.ENGLISH)).isEqualTo("Cash");
    }

    /** Le bean migré lit désormais le MessageSource dans la locale courante (et garde le
     *  fallback : code inconnu renvoyé tel quel, code vide → « — »). */
    @Test
    void payment_methods_bean_suit_la_locale() {
        try {
            LocaleContextHolder.setLocale(Locale.ENGLISH);
            assertThat(paymentMethods.label("ESPECES")).isEqualTo("Cash");
            LocaleContextHolder.setLocale(Locale.FRENCH);
            assertThat(paymentMethods.label("ESPECES")).isEqualTo("Espèces");
            assertThat(paymentMethods.label("CODE_INCONNU")).isEqualTo("CODE_INCONNU");
            assertThat(paymentMethods.label("")).isEqualTo("—");
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }
}
