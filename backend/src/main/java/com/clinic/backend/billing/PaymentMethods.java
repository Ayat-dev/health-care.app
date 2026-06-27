package com.clinic.backend.billing;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * Source unique des libellés conviviaux des modes de paiement.
 * <p>
 * {@code Payment.method} est un String libre (valeurs courantes : ESPECES, AMANATA,
 * MYNITA, VIREMENT, CARTE, ASSURANCE ; + valeurs héritées ORANGE_MONEY/WAVE/MTN_MOMO
 * conservées pour l'historique). Les vues affichent {@code @paymentMethods.label(code)}
 * au lieu du code brut.
 * <p>
 * Depuis l'i18n (P3.2 / slice 0), les libellés vivent dans les bundles sous
 * {@code paymethod.*} et sont résolus dans la locale courante. Un code inconnu est
 * renvoyé tel quel (jamais d'erreur) ; un code vide → {@code common.none} (« — »).
 */
@Component("paymentMethods")
public class PaymentMethods {

    private final MessageSource messageSource;

    public PaymentMethods(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /** Libellé convivial d'un code de mode de paiement (code inconnu → renvoyé tel quel). */
    public String label(String code) {
        if (code == null || code.isBlank()) {
            return messageSource.getMessage("common.none", null, "—", LocaleContextHolder.getLocale());
        }
        // defaultMessage = code → un mode hérité/inconnu sans clé reste affiché tel quel.
        return messageSource.getMessage("paymethod." + code, null, code, LocaleContextHolder.getLocale());
    }
}
