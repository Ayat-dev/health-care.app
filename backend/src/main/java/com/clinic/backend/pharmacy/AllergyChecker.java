package com.clinic.backend.pharmacy;

import com.clinic.backend.dto.AllergyWarningDto;
import com.clinic.backend.dto.DrugDto;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Recoupe les allergies (texte libre) d'un patient avec les médicaments qu'on s'apprête à
 * lui dispenser/prescrire (E2-A). <b>Purement consultatif, ne jette jamais</b> : il produit
 * des avertissements, la décision reste au professionnel de santé.
 * <p>
 * Heuristique volontairement simple et prudente (marché sans base pharmacologique) :
 * le texte d'allergie du patient, normalisé (minuscules + sans accents), <b>contient-il</b>
 * la classe allergène curée du médicament, sa DCI, ou son nom commercial ? Le vrai levier de
 * précision est la curation de {@code Drug.allergenClass} (ex. « Pénicilline »), qui recoupe
 * toute une famille indépendamment du nom commercial.
 */
@Component
public class AllergyChecker {

    private static final int MIN_TERM_LENGTH = 3; // évite les recoupements triviaux

    /** Avertissements pour la liste de médicaments donnée (jamais null). */
    public List<AllergyWarningDto> check(String patientAllergies, Collection<DrugDto> drugs) {
        List<AllergyWarningDto> out = new ArrayList<>();
        if (patientAllergies == null || patientAllergies.isBlank() || drugs == null) return out;
        String allergies = normalize(patientAllergies);
        for (DrugDto drug : drugs) {
            if (drug == null) continue;
            String matched = firstMatchingTerm(allergies, drug);
            if (matched != null) out.add(new AllergyWarningDto(drug.getName(), matched));
        }
        return out;
    }

    /** Renvoie le 1er terme du médicament (classe > DCI > nom) recoupé par les allergies, sinon null. */
    private String firstMatchingTerm(String normalizedAllergies, DrugDto drug) {
        for (String term : new String[]{drug.getAllergenClass(), drug.getGenericName(), drug.getName()}) {
            if (term == null || term.isBlank()) continue;
            String needle = normalize(term);
            if (needle.length() >= MIN_TERM_LENGTH && normalizedAllergies.contains(needle)) {
                return term; // on affiche le libellé d'origine (non normalisé)
            }
        }
        return null;
    }

    /** Minuscule + suppression des accents (comparaison robuste FR). */
    private static String normalize(String s) {
        String decomposed = Normalizer.normalize(s, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").toLowerCase().trim();
    }
}
