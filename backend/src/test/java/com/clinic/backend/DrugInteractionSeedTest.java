package com.clinic.backend;

import com.clinic.backend.dto.DrugDto;
import com.clinic.backend.dto.DrugInteractionDto;
import com.clinic.backend.dto.InteractionWarningDto;
import com.clinic.backend.pharmacy.DrugInteractionRepository;
import com.clinic.backend.pharmacy.InteractionChecker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2-B — vérifie que le catalogue d'interactions (référence GLOBALE, seedée par la migration
 * V36, non tenant-scopée → aucun tenant requis) est bien chargé, et qu'il déclenche pour la
 * paire seedée Warfarine + Ibuprofène.
 */
@SpringBootTest
@ActiveProfiles("test")
class DrugInteractionSeedTest {

    @Autowired DrugInteractionRepository repository;
    @Autowired InteractionChecker checker;

    private static DrugDto drug(long id, String name, String dci) {
        DrugDto d = new DrugDto();
        d.setId(id);
        d.setName(name);
        d.setGenericName(dci);
        return d;
    }

    @Test
    void le_catalogue_seede_charge_et_declenche() {
        List<DrugInteractionDto> rules = repository.findByActiveTrue().stream()
                .map(r -> new DrugInteractionDto(r.getDciA(), r.getDciB(), r.getSeverity(), r.getDescription()))
                .toList();
        assertThat(rules).isNotEmpty(); // seed V36 chargé

        List<InteractionWarningDto> w = checker.check(
                List.of(drug(1, "Coumadine", "Warfarine"), drug(2, "Nurofen", "Ibuprofène")), rules);
        assertThat(w).anyMatch(x -> "MAJEURE".equals(x.getSeverity()));
    }
}
