package com.clinic.backend;

import com.clinic.backend.billing.BillingService;
import com.clinic.backend.billing.Invoice;
import com.clinic.backend.dto.InvoiceDto;
import com.clinic.backend.dto.InvoiceItemDto;
import com.clinic.backend.dto.PaymentDto;
import com.clinic.backend.tenant.ClinicRepository;
import com.clinic.backend.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Moteur d'auto-facturation (P5.1 Lot A) — {@code BillingService.addCharge} : find-or-create
 * de la facture « ouverte » du patient, idempotence par acte source, et bascule {@code open}
 * au 1er encaissement. Exécuté sur H2 seedé, en transaction rollback (isolation).
 *
 * NB tenant (P4.2) : tenant figé à l'ouverture de session = début de tx de test → fixé en
 * {@code @BeforeTransaction} sur la clinique des comptes seedés (CENTRALE) ; le caissier
 * courant est résolu via {@code @WithUserDetails}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BillingAccumulationTest {

    private static final String UDS = "userDetailsServiceImpl";
    private static final long P1 = 1L; // patient seedé (CENTRALE), aucune facture ouverte (seed = open false)

    @Autowired BillingService billingService;
    @Autowired ClinicRepository clinicRepository;

    @BeforeTransaction
    void setTenant() {
        TenantContext.set(clinicRepository.findByCodeIgnoreCase("CENTRALE").orElseThrow().getId());
    }

    @AfterTransaction
    void clearTenant() {
        TenantContext.clear();
    }

    private static InvoiceItemDto line(String desc, String price) {
        InvoiceItemDto i = new InvoiceItemDto();
        i.setDescription(desc);
        i.setQuantity(1);
        i.setUnitPrice(new BigDecimal(price));
        return i;
    }

    @Test
    @WithUserDetails(value = "caissier", userDetailsServiceBeanName = UDS)
    void deux_actes_du_meme_patient_alimentent_une_seule_facture_ouverte() {
        Invoice a = billingService.addCharge(P1, "CONSULTATION", 999L, List.of(line("Consultation", "1000")));
        Invoice b = billingService.addCharge(P1, "LAB", 888L, List.of(line("Analyse", "500")));

        assertThat(a).isNotNull();
        assertThat(b.getId()).isEqualTo(a.getId());        // même facture ouverte
        assertThat(b.isOpen()).isTrue();
        assertThat(b.getItems()).hasSize(2);               // les deux actes empilés
        assertThat(b.getSubtotal()).isEqualByComparingTo("1500.00");
        assertThat(b.getStatus()).isEqualTo("EN_ATTENTE");
    }

    @Test
    @WithUserDetails(value = "caissier", userDetailsServiceBeanName = UDS)
    void refacturer_le_meme_acte_est_idempotent() {
        Invoice a = billingService.addCharge(P1, "CONSULTATION", 777L, List.of(line("Consultation", "1000")));
        Invoice again = billingService.addCharge(P1, "CONSULTATION", 777L, List.of(line("Consultation", "1000")));

        assertThat(again).isNull();                        // acte déjà facturé → no-op
        assertThat(billingService.getById(a.getId()).getItems()).hasSize(1); // pas de doublon
    }

    @Test
    @WithUserDetails(value = "caissier", userDetailsServiceBeanName = UDS)
    void le_paiement_ferme_la_facture_et_un_nouvel_acte_en_ouvre_une_autre() {
        Invoice open1 = billingService.addCharge(P1, "CONSULTATION", 555L, List.of(line("Consultation", "1000")));

        PaymentDto p = new PaymentDto();
        p.setAmount(new BigDecimal("1000"));               // solde la part patient (pas d'assurance)
        p.setMethod("ESPECES");
        Invoice paid = billingService.recordPayment(open1.getId(), p);

        assertThat(paid.getStatus()).isEqualTo("PAYE");
        assertThat(paid.isOpen()).isFalse();               // cesse d'accumuler

        Invoice open2 = billingService.addCharge(P1, "LAB", 444L, List.of(line("Analyse", "500")));
        assertThat(open2.getId()).isNotEqualTo(open1.getId()); // un nouvel acte → nouvelle facture
        assertThat(open2.isOpen()).isTrue();
    }

    @Test
    @WithUserDetails(value = "caissier", userDetailsServiceBeanName = UDS)
    void la_facture_ouverte_apparait_dans_la_file_caisse() {
        Invoice open1 = billingService.addCharge(P1, "CONSULTATION", 333L, List.of(line("Consultation", "1000")));

        List<InvoiceDto> queue = billingService.cashierQueue();
        assertThat(queue)
                .anyMatch(i -> i.getId().equals(open1.getId())
                        && i.isOpen()
                        && i.getBalanceDue().signum() > 0);
    }
}
