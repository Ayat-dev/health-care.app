package com.clinic.backend;

import com.clinic.backend.dto.DrugDto;
import com.clinic.backend.dto.DrugInteractionDto;
import com.clinic.backend.dto.InteractionWarningDto;
import com.clinic.backend.pharmacy.InteractionChecker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2-B — recoupement des interactions médicamenteuses. Pur (aucun Spring/base) : une règle
 * (paire de DCI) déclenche si deux médicaments DISTINCTS de la liste correspondent à ses deux
 * DCI (DCI ou nom, insensible aux accents/casse) ; ne jette jamais, ne bloque jamais.
 */
class InteractionCheckerTest {

    private final InteractionChecker checker = new InteractionChecker();

    private static DrugDto drug(long id, String name, String dci) {
        DrugDto d = new DrugDto();
        d.setId(id);
        d.setName(name);
        d.setGenericName(dci);
        return d;
    }

    private static DrugInteractionDto rule(String a, String b, String sev, String desc) {
        return new DrugInteractionDto(a, b, sev, desc);
    }

    @Test
    void detecte_une_paire_en_interaction() {
        List<InteractionWarningDto> w = checker.check(
                List.of(drug(1, "Coumadine", "Warfarine"), drug(2, "Nurofen", "Ibuprofène")),
                List.of(rule("Warfarine", "Ibuprofène", "MAJEURE", "risque hémorragique")));
        assertThat(w).hasSize(1);
        assertThat(w.get(0).getSeverity()).isEqualTo("MAJEURE");
        assertThat(w.get(0).getDescription()).contains("hémorragique");
        assertThat(List.of(w.get(0).getDrugA(), w.get(0).getDrugB()))
                .containsExactlyInAnyOrder("Coumadine", "Nurofen"); // noms commerciaux
    }

    @Test
    void un_seul_medicament_de_la_paire_pas_d_alerte() {
        List<InteractionWarningDto> w = checker.check(
                List.of(drug(1, "Coumadine", "Warfarine"), drug(2, "Doliprane", "Paracétamol")),
                List.of(rule("Warfarine", "Ibuprofène", "MAJEURE", "x")));
        assertThat(w).isEmpty();
    }

    @Test
    void insensible_aux_accents_et_a_l_ordre() {
        List<InteractionWarningDto> w = checker.check(
                List.of(drug(1, "A", "ibuprofene"), drug(2, "B", "warfarine")), // sans accents, ordre inverse
                List.of(rule("Warfarine", "Ibuprofène", "MODEREE", "x")));
        assertThat(w).hasSize(1);
    }

    @Test
    void un_seul_medicament_correspondant_aux_deux_dci_ne_se_declenche_pas() {
        // Un médicament dont la DCI recoupe les deux termes ne doit pas s'auto-interagir.
        List<InteractionWarningDto> w = checker.check(
                List.of(drug(1, "Combo", "Warfarine Ibuprofène"), drug(2, "Doliprane", "Paracétamol")),
                List.of(rule("Warfarine", "Ibuprofène", "MAJEURE", "x")));
        assertThat(w).isEmpty(); // a == b (même médicament) → ignoré
    }

    @Test
    void entrees_insuffisantes_ou_nulles_sans_erreur() {
        List<DrugInteractionDto> rules = List.of(rule("Warfarine", "Ibuprofène", "MAJEURE", "x"));
        assertThat(checker.check(List.of(drug(1, "A", "Warfarine")), rules)).isEmpty(); // < 2 médicaments
        assertThat(checker.check(null, rules)).isEmpty();
        assertThat(checker.check(List.of(drug(1, "A", "Warfarine"), drug(2, "B", "Ibuprofène")), null)).isEmpty();
    }
}
