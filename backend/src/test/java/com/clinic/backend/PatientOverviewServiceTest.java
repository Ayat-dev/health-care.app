package com.clinic.backend;

import com.clinic.backend.consultation.Consultation;
import com.clinic.backend.dto.*;
import com.clinic.backend.i18n.WebI18n;
import com.clinic.backend.model.User;
import com.clinic.backend.patient.Patient;
import com.clinic.backend.patient.PatientOverviewService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Coup d'œil patient + timeline (P3.6) : dérivation des alertes (priorité RED→ORANGE→INFO),
 * dernières constantes, et agrégation/tri de la timeline. Pur, sans Spring ni base — mais
 * les libellés (alertes/catégories) sont désormais i18n (slice 1), donc on câble un
 * MessageSource réel sur le bundle FR et on force la locale française pour les assertions.
 */
class PatientOverviewServiceTest {

    private final PatientOverviewService service;

    {
        ResourceBundleMessageSource ms = new ResourceBundleMessageSource();
        ms.setBasename("messages");
        ms.setDefaultEncoding("UTF-8");
        service = new PatientOverviewService(new WebI18n(ms));
    }

    @BeforeEach
    void frenchLocale() {
        LocaleContextHolder.setLocale(Locale.FRENCH);
    }

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void agrege_les_alertes_et_trie_la_timeline() {
        Patient p = new Patient();
        p.setBirthDate(LocalDate.now().minusYears(30));
        p.setBloodType("O+");
        p.setAllergies("Pénicilline");
        p.setChronicConditions("Diabète type 2");

        User dr = new User();
        dr.setFullName("Dr Martin");

        Consultation c = new Consultation();
        c.setId(7L);
        c.setConsultationDate(LocalDateTime.now().minusDays(1));
        c.setDoctor(dr);
        c.setChiefComplaint("Fièvre");
        c.setDiagnosis("Paludisme");
        c.setStatus("TERMINE");
        c.setBpSystolic(150);   // > 140 → tension élevée
        c.setBpDiastolic(95);
        c.setTemperatureC(new BigDecimal("38.5"));

        LabRequestDto lab = new LabRequestDto();
        lab.setId(3L);
        lab.setRequestNumber("LAB-1");
        lab.setRequestedAt(LocalDateTime.now().minusDays(2));
        lab.setAbnormalCount(2);
        lab.setStatus("VALIDE");

        HospitalizationDto stay = new HospitalizationDto();
        stay.setId(5L);
        stay.setRoomNumber("102");
        stay.setStatus("ADMIS");          // hospitalisé actuellement
        stay.setAdmissionDate(LocalDateTime.now().minusDays(3));
        stay.setNights(3);

        InvoiceDto inv = new InvoiceDto();
        inv.setId(9L);
        inv.setInvoiceNumber("FAC-1");
        inv.setStatus("PARTIEL");          // impayé
        inv.setCreatedAt(LocalDateTime.now().minusHours(2)); // l'évènement le plus récent
        inv.setPatientAmount(new BigDecimal("5000"));
        inv.setBalanceDue(new BigDecimal("800"));

        PatientOverviewDto o = service.build(
                p, List.of(c), List.of(lab), List.of(), List.of(stay), List.of(inv), null);

        // Constantes
        assertThat(o.getAgeYears()).isEqualTo(30);
        assertThat(o.isHasVitals()).isTrue();
        assertThat(o.getBloodPressure()).isEqualTo("150/95");

        // Alertes : allergie (RED), hospit, tension, labo, impayé (ORANGE), antécédents (INFO)
        assertThat(o.getAlerts()).hasSizeGreaterThanOrEqualTo(6);
        assertThat(o.getAlerts().get(0).getLevel()).isEqualTo("RED");   // RED en tête
        assertThat(o.getAlerts()).extracting(OverviewAlertDto::getMessage)
                .anyMatch(m -> m.contains("Allergies"))
                .anyMatch(m -> m.contains("hospitalisé"))
                .anyMatch(m -> m.contains("Tension"))
                .anyMatch(m -> m.contains("anormaux"))
                .anyMatch(m -> m.contains("impayée"));

        // Timeline : 4 évènements, le plus récent (facture, -2h) en tête
        assertThat(o.getTimeline()).hasSize(4);
        assertThat(o.getTimeline().get(0).getCategory()).isEqualTo("Facturation");
        assertThat(o.getTimeline().get(3).getCategory()).isEqualTo("Hospitalisation"); // le plus ancien
    }

    @Test
    void patient_vierge_aucune_alerte_aucun_evenement() {
        Patient p = new Patient();
        PatientOverviewDto o = service.build(
                p, List.of(), List.of(), List.of(), List.of(), List.of(), null);

        assertThat(o.getAlerts()).isEmpty();
        assertThat(o.getTimeline()).isEmpty();
        assertThat(o.isHasVitals()).isFalse();
        assertThat(o.isHasAllergies()).isFalse();
    }
}
