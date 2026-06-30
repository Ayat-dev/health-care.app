package com.clinic.backend.search;

import com.clinic.backend.appointment.AppointmentRepository;
import com.clinic.backend.billing.InvoiceRepository;
import com.clinic.backend.config.Module;
import com.clinic.backend.config.RoleProfile;
import com.clinic.backend.consultation.ConsultationRepository;
import com.clinic.backend.dto.SearchResultDto;
import com.clinic.backend.patient.PatientRepository;
import com.clinic.backend.pharmacy.DrugRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Recherche globale (P3.5) — alimente la palette de commandes du chrome.
 * <p>
 * Agrège plusieurs sources en une liste unique de {@link SearchResultDto}, en
 * respectant la visibilité par rôle (mêmes règles que la sidebar via
 * {@link RoleProfile}) : un PHARMACIEN ne voit ni patients ni factures.
 * <ul>
 *   <li><b>Navigation</b> — modules du rôle dont le libellé contient la requête (0 DB)</li>
 *   <li><b>Patients</b> — si le rôle a accès au module PATIENTS</li>
 *   <li><b>Consultations</b> — si le rôle a accès au module CONSULTATIONS (par patient / code CIM-10)</li>
 *   <li><b>Rendez-vous</b> — si le rôle a accès au module APPOINTMENTS (par patient)</li>
 *   <li><b>Factures</b> — si le rôle a accès au module BILLING</li>
 *   <li><b>Médicaments</b> — si le rôle a accès au module PHARMACY</li>
 * </ul>
 * Mapping entité→DTO <b>dans la transaction</b> (OSIV off) : les libellés
 * de catégorie sont résolus via {@link MessageSource} dans la locale courante.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GlobalSearchService {

    /** En-dessous, une recherche DB ratisse trop large : la navigation reste dispo dès 1 car. */
    private static final int MIN_DB_QUERY = 2;
    private static final int PER_CATEGORY = 6;

    private final PatientRepository patientRepository;
    private final InvoiceRepository invoiceRepository;
    private final ConsultationRepository consultationRepository;
    private final AppointmentRepository appointmentRepository;
    private final DrugRepository drugRepository;
    private final MessageSource messages;

    public List<SearchResultDto> search(String rawQuery, String role) {
        List<SearchResultDto> out = new ArrayList<>();
        String q = rawQuery == null ? "" : rawQuery.trim();
        if (q.isEmpty()) return out;

        Locale locale = LocaleContextHolder.getLocale();
        RoleProfile profile = RoleProfile.fromRole(role);
        String ql = q.toLowerCase(locale);

        // ── 1. Navigation (en mémoire, dès le 1er caractère) ─────────────────
        String navCat = msg("search.section.nav", locale);
        for (Module m : profile.orderedModules()) {
            String label = msg(m.labelKey, locale);
            if (label.toLowerCase(locale).contains(ql)) {
                out.add(new SearchResultDto("NAV", navCat, label, null, m.urlPrefix, m.icon));
            }
        }

        // ── Catégories DB : à partir de 2 caractères ─────────────────────────
        if (q.length() < MIN_DB_QUERY) return out;

        // ── 2. Patients ──────────────────────────────────────────────────────
        if (profile.modules.contains(Module.PATIENTS)) {
            String cat = msg("search.section.patients", locale);
            patientRepository.search(q, PageRequest.of(0, PER_CATEGORY)).getContent()
                    .forEach(p -> out.add(new SearchResultDto(
                            "PATIENT", cat,
                            p.getLastName() + " " + p.getFirstName(),
                            p.getRecordNumber(),
                            "/patients/" + p.getId(),
                            "👤")));
        }

        // ── 3. Consultations ─────────────────────────────────────────────────
        if (profile.modules.contains(Module.CONSULTATIONS)) {
            String cat = msg("search.section.consultations", locale);
            consultationRepository.searchForPalette(q, PageRequest.of(0, PER_CATEGORY))
                    .forEach(c -> out.add(new SearchResultDto(
                            "CONSULTATION", cat,
                            c.getPatient() != null
                                    ? c.getPatient().getLastName() + " " + c.getPatient().getFirstName()
                                    : "#" + c.getId(),
                            c.getConsultationDate() != null
                                    ? c.getConsultationDate().toLocalDate().toString() : null,
                            "/consultations/" + c.getId(),
                            "🩺")));
        }

        // ── 4. Rendez-vous ───────────────────────────────────────────────────
        if (profile.modules.contains(Module.APPOINTMENTS)) {
            String cat = msg("search.section.appointments", locale);
            appointmentRepository.searchForPalette(q, PageRequest.of(0, PER_CATEGORY))
                    .forEach(a -> out.add(new SearchResultDto(
                            "APPOINTMENT", cat,
                            a.getPatient() != null
                                    ? a.getPatient().getLastName() + " " + a.getPatient().getFirstName()
                                    : "#" + a.getId(),
                            a.getStartTime() != null
                                    ? a.getStartTime().toString().replace('T', ' ') : null,
                            "/appointments/" + a.getId() + "/edit",
                            "📅")));
        }

        // ── 5. Factures ──────────────────────────────────────────────────────
        if (profile.modules.contains(Module.BILLING)) {
            String cat = msg("search.section.invoices", locale);
            invoiceRepository.searchByNumber(q, PageRequest.of(0, PER_CATEGORY))
                    .forEach(inv -> out.add(new SearchResultDto(
                            "INVOICE", cat,
                            inv.getInvoiceNumber(),
                            inv.getPatient() != null
                                    ? inv.getPatient().getLastName() + " " + inv.getPatient().getFirstName()
                                    : null,
                            "/billing/invoices/" + inv.getId(),
                            "💳")));
        }

        // ── 6. Médicaments ───────────────────────────────────────────────────
        if (profile.modules.contains(Module.PHARMACY)) {
            String cat = msg("search.section.drugs", locale);
            drugRepository.search(q, null).stream().limit(PER_CATEGORY)
                    .forEach(d -> out.add(new SearchResultDto(
                            "DRUG", cat,
                            d.getName(),
                            d.getDosageStrength() != null ? d.getDosageStrength() : d.getForm(),
                            "/pharmacy/drugs/" + d.getId() + "/edit",
                            "💊")));
        }

        return out;
    }

    private String msg(String key, Locale locale) {
        return messages.getMessage(key, null, key, locale);
    }
}
