package com.clinic.backend.consultation;

import com.clinic.backend.clinicconfig.ClinicConfig;
import com.clinic.backend.clinicconfig.ClinicConfigService;
import com.clinic.backend.dto.PrescriptionDto;
import com.clinic.backend.dto.PrescriptionItemDto;
import com.clinic.backend.model.User;
import com.clinic.backend.patient.Patient;
import com.clinic.backend.patient.PatientRepository;
import com.clinic.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class PrescriptionService {

    private final PrescriptionRepository prescriptionRepository;
    private final ConsultationRepository consultationRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final ClinicConfigService clinicConfigService;

    // ── Détail ────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public Prescription getById(Long id) {
        return prescriptionRepository.findWithRefsById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ordonnance introuvable : " + id));
    }

    @Transactional(readOnly = true)
    public PrescriptionDto getDtoById(Long id) {
        return toDto(getById(id));
    }

    /** The latest ordonnance for a consultation, mapped to a DTO; null if none yet. */
    @Transactional(readOnly = true)
    public PrescriptionDto findDtoForConsultation(Long consultationId) {
        List<Prescription> found = prescriptionRepository.findByConsultation(consultationId);
        return found.isEmpty() ? null : toDto(found.get(0));
    }

    // ── Création depuis une consultation ───────────────────────────────────
    public Prescription createForConsultation(Long consultationId, PrescriptionDto dto) {
        Consultation c = consultationRepository.findWithRefsById(consultationId)
                .orElseThrow(() -> new IllegalArgumentException("Consultation introuvable : " + consultationId));

        Prescription p = new Prescription();
        p.setConsultation(c);
        p.setPatient(c.getPatient());
        p.setDoctor(c.getDoctor());
        p.setPrescriptionNumber(nextNumber());
        applyHeader(dto, p);
        replaceItems(p, dto.getItems());
        return prescriptionRepository.save(p);
    }

    // ── Modification ────────────────────────────────────────────────────────
    public Prescription update(Long id, PrescriptionDto dto) {
        Prescription p = getById(id);
        applyHeader(dto, p);
        replaceItems(p, dto.getItems());
        return prescriptionRepository.save(p);
    }

    private void applyHeader(PrescriptionDto dto, Prescription p) {
        p.setIssueDate(dto.getIssueDate() != null ? dto.getIssueDate() : LocalDate.now());
        p.setValidityDays(dto.getValidityDays() > 0 ? dto.getValidityDays() : 30);
        p.setNotes(dto.getNotes());
    }

    /** Replace the full item list (orphanRemoval clears the old ones). Empty rows skipped. */
    private void replaceItems(Prescription p, List<PrescriptionItemDto> items) {
        p.getItems().clear();
        if (items == null) return;
        int order = 0;
        for (PrescriptionItemDto it : items) {
            if (it == null || it.getDrugName() == null || it.getDrugName().isBlank()) {
                continue; // ligne vide
            }
            PrescriptionItem item = new PrescriptionItem();
            item.setDrugId(it.getDrugId());
            item.setDrugName(it.getDrugName().trim());
            item.setDosage(it.getDosage() != null ? it.getDosage().trim() : "");
            item.setFrequency(it.getFrequency() != null ? it.getFrequency().trim() : "");
            item.setDuration(it.getDuration());
            item.setQuantity(it.getQuantity());
            item.setInstructions(it.getInstructions());
            item.setSortOrder(order++);
            p.addItem(item);
        }
        if (p.getItems().isEmpty()) {
            throw new IllegalArgumentException("L'ordonnance doit contenir au moins un médicament");
        }
    }

    // ── Numérotation ORD-YYYY-NNNNN ───────────────────────────────────────────
    private String nextNumber() {
        ClinicConfig config = clinicConfigService.getConfig();
        String prefix = config.getPrescriptionPrefix() + "-" + Year.now().getValue() + "-";
        int next = prescriptionRepository.findMaxSequence(prefix) + 1;
        return prefix + String.format("%05d", next);
    }

    // ── Dispensation (le module Pharmacie posera le drapeau) ──────────────────
    public Prescription markDispensed(Long id) {
        Prescription p = getById(id);
        p.setDispensed(true);
        p.setDispensedAt(LocalDateTime.now());
        return prescriptionRepository.save(p);
    }

    // ── DTO depuis entité (associations initialisées requises) ────────────────
    public PrescriptionDto toDto(Prescription p) {
        PrescriptionDto dto = new PrescriptionDto();
        dto.setId(p.getId());
        dto.setPrescriptionNumber(p.getPrescriptionNumber());
        dto.setConsultationId(p.getConsultation() != null ? p.getConsultation().getId() : null);
        dto.setPatientId(p.getPatient() != null ? p.getPatient().getId() : null);
        dto.setDoctorId(p.getDoctor() != null ? p.getDoctor().getId() : null);
        dto.setIssueDate(p.getIssueDate());
        dto.setValidityDays(p.getValidityDays());
        dto.setNotes(p.getNotes());
        dto.setDispensed(p.isDispensed());
        if (p.getPatient() != null) {
            dto.setPatientName(p.getPatient().getFullName());
            dto.setPatientRecordNumber(p.getPatient().getRecordNumber());
        }
        dto.setDoctorName(p.getDoctor() != null ? p.getDoctor().getFullName() : null);
        for (PrescriptionItem it : p.getItems()) {
            PrescriptionItemDto idto = new PrescriptionItemDto();
            idto.setId(it.getId());
            idto.setDrugId(it.getDrugId());
            idto.setDrugName(it.getDrugName());
            idto.setDosage(it.getDosage());
            idto.setFrequency(it.getFrequency());
            idto.setDuration(it.getDuration());
            idto.setQuantity(it.getQuantity());
            idto.setInstructions(it.getInstructions());
            idto.setSortOrder(it.getSortOrder());
            dto.getItems().add(idto);
        }
        return dto;
    }
}
