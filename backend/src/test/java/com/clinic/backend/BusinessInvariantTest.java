package com.clinic.backend;

import com.clinic.backend.billing.BillingService;
import com.clinic.backend.billing.Invoice;
import com.clinic.backend.dto.*;
import com.clinic.backend.pharmacy.Drug;
import com.clinic.backend.pharmacy.PharmacyService;
import com.clinic.backend.pharmacy.StockItem;
import com.clinic.backend.pharmacy.StockItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Invariants métier (P1.5) — exécutés sur H2 seedé, en transaction rollback
 * pour l'isolation. Couvre : couverture assurance, garde de surpaiement,
 * transition interdite (facture soldée), FIFO pharmacie, stock insuffisant.
 *
 * NB : les services résolvent l'utilisateur courant via
 * {@code SecurityContextHolder} → chaque méthode tourne sous un compte seedé
 * via {@code @WithUserDetails}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BusinessInvariantTest {

    private static final String UDS = "userDetailsServiceImpl";

    @Autowired BillingService billingService;
    @Autowired PharmacyService pharmacyService;
    @Autowired StockItemRepository stockItemRepository;

    // ── Facturation ──────────────────────────────────────────────────────────

    @Test
    @WithUserDetails(value = "caissier", userDetailsServiceBeanName = UDS)
    void couverture_assurance_repartit_le_subtotal() {
        InvoiceDto dto = new InvoiceDto();
        dto.setPatientId(1L);                                  // p1 seedé
        dto.setInsuranceCoveragePercent(new BigDecimal("80"));
        InvoiceItemDto item = new InvoiceItemDto();
        item.setDescription("Consultation générale");
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("1000"));
        dto.setItems(List.of(item));

        Invoice inv = billingService.create(dto);

        assertThat(inv.getSubtotal()).isEqualByComparingTo("1000.00");
        assertThat(inv.getInsuranceAmount()).isEqualByComparingTo("800.00");
        assertThat(inv.getPatientAmount()).isEqualByComparingTo("200.00");
        assertThat(inv.getStatus()).isEqualTo("EN_ATTENTE");
    }

    @Test
    @WithUserDetails(value = "caissier", userDetailsServiceBeanName = UDS)
    void surpaiement_rejete() {
        PaymentDto p = new PaymentDto();
        p.setAmount(new BigDecimal("99999999"));
        p.setMethod("ESPECES");
        // inv1 seedé = PARTIEL (solde > 0 mais < 99999999)
        assertThatThrownBy(() -> billingService.recordPayment(1L, p))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @WithUserDetails(value = "caissier", userDetailsServiceBeanName = UDS)
    void paiement_sur_facture_soldee_rejete() {
        PaymentDto p = new PaymentDto();
        p.setAmount(new BigDecimal("100"));
        p.setMethod("ESPECES");
        // inv2 seedé = PAYE
        assertThatThrownBy(() -> billingService.recordPayment(2L, p))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── Pharmacie ──────────────────────────────────────────────────────────────

    @Test
    @WithUserDetails(value = "pharmacien", userDetailsServiceBeanName = UDS)
    void dispensation_fifo_vide_le_lot_le_plus_proche_de_la_peremption() {
        Drug drug = newDrug("Test-FIFO");
        receive(drug.getId(), LocalDate.now().plusDays(30), 10);  // proche péremption
        receive(drug.getId(), LocalDate.now().plusYears(2), 10);  // lointain

        dispense(drug.getId(), 15);

        List<StockItem> remaining = stockItemRepository.findAvailableForDrug(drug.getId(), LocalDate.now());
        int total = remaining.stream().mapToInt(StockItem::getQuantity).sum();
        assertThat(total).isEqualTo(5);                           // 20 - 15
        assertThat(remaining).hasSize(1);                         // le lot proche est vidé
        assertThat(remaining.get(0).getExpiryDate())
                .isAfter(LocalDate.now().plusDays(60));           // seul reste le lot lointain
    }

    @Test
    @WithUserDetails(value = "pharmacien", userDetailsServiceBeanName = UDS)
    void dispensation_insuffisante_rejetee() {
        Drug drug = newDrug("Test-LOW");
        receive(drug.getId(), LocalDate.now().plusYears(1), 3);
        assertThatThrownBy(() -> dispense(drug.getId(), 99))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Drug newDrug(String name) {
        DrugDto d = new DrugDto();
        d.setName(name);
        d.setUnit("comprimé");
        d.setRequiresPrescription(false);
        return pharmacyService.createDrug(d);
    }

    private void receive(Long drugId, LocalDate expiry, int qty) {
        StockItemDto s = new StockItemDto();
        s.setDrugId(drugId);
        s.setExpiryDate(expiry);
        s.setQuantity(qty);
        s.setSellingPrice(new BigDecimal("100"));
        pharmacyService.receiveStock(s);
    }

    private void dispense(Long drugId, int qty) {
        DispensationDto d = new DispensationDto();
        d.setPatientId(1L);
        DispensationItemDto line = new DispensationItemDto();
        line.setDrugId(drugId);
        line.setQuantity(qty);
        d.setItems(List.of(line));
        pharmacyService.dispense(d);
    }
}
