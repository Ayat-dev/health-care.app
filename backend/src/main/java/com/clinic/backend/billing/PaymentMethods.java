package com.clinic.backend.billing;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Source unique des libellés conviviaux des modes de paiement.
 * <p>
 * {@code Payment.method} est un String libre (valeurs courantes : ESPECES, AMANATA,
 * MYNITA, VIREMENT, CARTE, ASSURANCE ; + valeurs héritées ORANGE_MONEY/WAVE/MTN_MOMO
 * conservées pour l'historique). Les vues affichent {@code @paymentMethods.label(code)}
 * au lieu du code brut. Un code inconnu est renvoyé tel quel (jamais d'erreur).
 */
@Component("paymentMethods")
public class PaymentMethods {

    private static final Map<String, String> LABELS = new LinkedHashMap<>();
    static {
        LABELS.put("ESPECES", "Espèces");
        LABELS.put("AMANATA", "AmanaTa");
        LABELS.put("MYNITA", "MyNITA");
        LABELS.put("VIREMENT", "Virement bancaire");
        LABELS.put("CARTE", "Carte bancaire");
        LABELS.put("ASSURANCE", "Assurance / Tiers payant");
        // Héritées (paiements déjà enregistrés avant le passage au marché Niger)
        LABELS.put("ORANGE_MONEY", "Orange Money");
        LABELS.put("WAVE", "Wave");
        LABELS.put("MTN_MOMO", "MTN MoMo");
    }

    /** Libellé convivial d'un code de mode de paiement (code inconnu → renvoyé tel quel). */
    public String label(String code) {
        if (code == null || code.isBlank()) return "—";
        return LABELS.getOrDefault(code, code);
    }
}
