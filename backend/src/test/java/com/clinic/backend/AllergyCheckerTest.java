package com.clinic.backend;

import com.clinic.backend.dto.AllergyWarningDto;
import com.clinic.backend.dto.DrugDto;
import com.clinic.backend.pharmacy.AllergyChecker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2-A — recoupement allergies ↔ médicaments. Pur (aucun Spring, aucune base) : classe
 * allergène curée, DCI, ou nom commercial contenus dans le texte d'allergie du patient ;
 * insensible aux accents/casse ; ne jette jamais et ne bloque jamais.
 */
class AllergyCheckerTest {

    private final AllergyChecker checker = new AllergyChecker();

    private static DrugDto drug(String name, String dci, String allergenClass) {
        DrugDto d = new DrugDto();
        d.setId((long) name.hashCode());
        d.setName(name);
        d.setGenericName(dci);
        d.setAllergenClass(allergenClass);
        return d;
    }

    @Test
    void recoupe_par_classe_allergene_insensible_aux_accents() {
        // Patient allergique à « Pénicilline » ; Amoxicilline taguée classe « Penicilline ».
        List<AllergyWarningDto> w = checker.check("Penicilline",
                List.of(drug("Amoxicilline", "Amoxicilline", "Pénicilline")));
        assertThat(w).hasSize(1);
        assertThat(w.get(0).getDrugName()).isEqualTo("Amoxicilline");
        assertThat(w.get(0).getAllergen()).isEqualTo("Pénicilline");
    }

    @Test
    void recoupe_par_dci_ou_nom_commercial() {
        List<AllergyWarningDto> byDci = checker.check("allergie à l'ibuprofène",
                List.of(drug("Nurofen", "Ibuprofène", null)));
        assertThat(byDci).hasSize(1);

        List<AllergyWarningDto> byName = checker.check("réaction au Doliprane",
                List.of(drug("Doliprane", "Paracétamol", null)));
        assertThat(byName).hasSize(1);
    }

    @Test
    void aucun_recoupement_pas_d_avertissement() {
        List<AllergyWarningDto> w = checker.check("Pollen, arachide",
                List.of(drug("Paracétamol", "Paracétamol", "Paracétamol")));
        assertThat(w).isEmpty();
    }

    @Test
    void allergies_vides_ou_nulles_sans_erreur() {
        List<DrugDto> drugs = List.of(drug("Amoxicilline", "Amoxicilline", "Pénicilline"));
        assertThat(checker.check(null, drugs)).isEmpty();
        assertThat(checker.check("   ", drugs)).isEmpty();
        assertThat(checker.check("Pénicilline", null)).isEmpty();
    }

    @Test
    void plusieurs_medicaments_seuls_les_concernes_remontent() {
        List<AllergyWarningDto> w = checker.check("Pénicilline",
                List.of(drug("Amoxicilline", "Amoxicilline", "Pénicilline"),
                        drug("Paracétamol", "Paracétamol", null),
                        drug("Oméprazole", "Oméprazole", null)));
        assertThat(w).extracting(AllergyWarningDto::getDrugName).containsExactly("Amoxicilline");
    }
}
