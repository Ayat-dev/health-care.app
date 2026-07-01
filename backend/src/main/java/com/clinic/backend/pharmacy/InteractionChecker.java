package com.clinic.backend.pharmacy;

import com.clinic.backend.dto.DrugDto;
import com.clinic.backend.dto.DrugInteractionDto;
import com.clinic.backend.dto.InteractionWarningDto;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Recoupe une liste de médicaments (prescrits/dispensés) avec un catalogue de règles
 * d'interaction par paire de DCI (E2-B). <b>Purement consultatif, ne jette jamais</b> :
 * la décision reste au professionnel de santé.
 * <p>
 * Frère de {@link AllergyChecker} : même normalisation (minuscules + sans accents). Un
 * médicament « correspond » à une DCI de règle si sa DCI ou son nom commercial contient
 * le terme normalisé (≥ 3 car.). Une règle déclenche un avertissement si <b>deux médicaments
 * distincts</b> de la liste correspondent respectivement à ses deux DCI.
 */
@Component
public class InteractionChecker {

    private static final int MIN_TERM_LENGTH = 3;

    public List<InteractionWarningDto> check(Collection<DrugDto> drugs,
                                             Collection<DrugInteractionDto> rules) {
        List<InteractionWarningDto> out = new ArrayList<>();
        if (drugs == null || rules == null) return out;
        List<DrugDto> list = drugs.stream().filter(d -> d != null && d.getId() != null).toList();
        if (list.size() < 2) return out; // il faut au moins deux médicaments pour une interaction
        for (DrugInteractionDto rule : rules) {
            if (rule == null) continue;
            DrugDto a = firstMatching(list, rule.getDciA());
            DrugDto b = firstMatching(list, rule.getDciB());
            if (a != null && b != null && !a.getId().equals(b.getId())) {
                out.add(new InteractionWarningDto(a.getName(), b.getName(),
                        rule.getSeverity(), rule.getDescription()));
            }
        }
        return out;
    }

    private DrugDto firstMatching(List<DrugDto> drugs, String dci) {
        if (dci == null || dci.isBlank()) return null;
        String needle = normalize(dci);
        if (needle.length() < MIN_TERM_LENGTH) return null;
        for (DrugDto d : drugs) {
            if (contains(d.getGenericName(), needle) || contains(d.getName(), needle)) return d;
        }
        return null;
    }

    private static boolean contains(String field, String normalizedNeedle) {
        return field != null && normalize(field).contains(normalizedNeedle);
    }

    private static String normalize(String s) {
        String decomposed = Normalizer.normalize(s, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").toLowerCase().trim();
    }
}
