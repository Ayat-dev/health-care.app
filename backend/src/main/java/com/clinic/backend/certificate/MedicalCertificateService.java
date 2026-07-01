package com.clinic.backend.certificate;

import com.clinic.backend.clinicconfig.ClinicConfigService;
import com.clinic.backend.config.ResourceNotFoundException;
import com.clinic.backend.consultation.Consultation;
import com.clinic.backend.consultation.ConsultationRepository;
import com.clinic.backend.dto.MedicalCertificateDto;
import com.clinic.backend.model.User;
import com.clinic.backend.patient.Patient;
import com.clinic.backend.patient.PatientRepository;
import com.clinic.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Year;
import java.util.List;

/**
 * Émission des certificats médicaux (Tier E1). Numérotation CERT-YYYY-NNNNN (préfixe constant,
 * comme le labo). Le médecin émetteur = l'utilisateur courant ; le corps est saisi manuellement
 * (aucun diagnostic injecté — confidentialité).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MedicalCertificateService {

    /** Préfixe de numérotation (pas de champ dédié dans clinic_config, comme LAB). */
    private static final String PREFIX = "CERT";

    /** Types disponibles (codes contrôlés ; libellés via i18n {@code certificates.type.*}). */
    public static final List<String> TYPES = List.of(
            "GENERAL", "ARRET_TRAVAIL", "REPOS", "APTITUDE", "PRESENCE", "BONNE_SANTE", "GROSSESSE");

    private final MedicalCertificateRepository certificateRepository;
    private final ConsultationRepository consultationRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final ClinicConfigService clinicConfigService;

    // ── Lecture ────────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public MedicalCertificate getById(Long id) {
        return certificateRepository.findWithRefsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Certificat introuvable : " + id));
    }

    @Transactional(readOnly = true)
    public MedicalCertificateDto getDtoById(Long id) {
        return toDto(getById(id));
    }

    @Transactional(readOnly = true)
    public List<MedicalCertificateDto> findForPatient(Long patientId) {
        return certificateRepository.findByPatient(patientId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<MedicalCertificateDto> recent(int limit) {
        return certificateRepository.findRecent(PageRequest.of(0, limit)).stream().map(this::toDto).toList();
    }

    // ── Pré-remplissage (depuis une consultation ou un patient) ─────────────────
    @Transactional(readOnly = true)
    public MedicalCertificateDto prefill(Long consultationId, Long patientId) {
        MedicalCertificateDto dto = new MedicalCertificateDto();
        dto.setType("GENERAL");
        dto.setIssueDate(LocalDate.now());
        if (consultationId != null) {
            Consultation c = consultationRepository.findWithRefsById(consultationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Consultation introuvable : " + consultationId));
            dto.setConsultationId(c.getId());
            if (c.getPatient() != null) {
                dto.setPatientId(c.getPatient().getId());
                dto.setPatientName(c.getPatient().getFullName());
                dto.setPatientRecordNumber(c.getPatient().getRecordNumber());
            }
            if (c.getDoctor() != null) dto.setDoctorName(c.getDoctor().getFullName());
        } else if (patientId != null) {
            Patient p = patientRepository.findById(patientId)
                    .orElseThrow(() -> new ResourceNotFoundException("Patient introuvable : " + patientId));
            dto.setPatientId(p.getId());
            dto.setPatientName(p.getFullName());
            dto.setPatientRecordNumber(p.getRecordNumber());
        }
        return dto;
    }

    // ── Création ────────────────────────────────────────────────────────────────
    public MedicalCertificate create(MedicalCertificateDto dto) {
        if (dto.getPatientId() == null) {
            throw new IllegalArgumentException("Le patient est obligatoire");
        }
        Patient patient = patientRepository.findById(dto.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient introuvable : " + dto.getPatientId()));

        MedicalCertificate cert = new MedicalCertificate();
        cert.setPatient(patient);
        cert.setDoctor(requireCurrentUser());   // médecin émetteur = utilisateur courant
        if (dto.getConsultationId() != null) {
            consultationRepository.findById(dto.getConsultationId()).ifPresent(cert::setConsultation);
        }
        cert.setCertificateNumber(nextNumber());
        apply(dto, cert);
        return certificateRepository.save(cert);
    }

    // ── Modification (n'altère ni patient, ni médecin, ni numéro) ────────────────
    public MedicalCertificate update(Long id, MedicalCertificateDto dto) {
        MedicalCertificate cert = getById(id);
        apply(dto, cert);
        return certificateRepository.save(cert);
    }

    private void apply(MedicalCertificateDto dto, MedicalCertificate cert) {
        String type = (dto.getType() != null && TYPES.contains(dto.getType())) ? dto.getType() : "GENERAL";
        cert.setType(type);
        cert.setIssueDate(dto.getIssueDate() != null ? dto.getIssueDate() : LocalDate.now());
        cert.setContent(dto.getContent());
        // Dates de repos : pertinentes seulement pour arrêt de travail / repos médical.
        boolean rest = "ARRET_TRAVAIL".equals(type) || "REPOS".equals(type);
        cert.setRestStartDate(rest ? dto.getRestStartDate() : null);
        cert.setRestEndDate(rest ? dto.getRestEndDate() : null);
        cert.setRestDays(rest ? computeRestDays(dto) : null);
    }

    /** Nombre de jours de repos : explicite si fourni, sinon dérivé des bornes (inclusives). */
    private Integer computeRestDays(MedicalCertificateDto dto) {
        if (dto.getRestDays() != null && dto.getRestDays() > 0) return dto.getRestDays();
        LocalDate s = dto.getRestStartDate(), e = dto.getRestEndDate();
        if (s != null && e != null && !e.isBefore(s)) {
            return (int) (e.toEpochDay() - s.toEpochDay()) + 1; // bornes inclusives
        }
        return null;
    }

    // ── Numérotation CERT-YYYY-NNNNN ─────────────────────────────────────────────
    private String nextNumber() {
        String prefix = PREFIX + "-" + Year.now().getValue() + "-";
        int next = certificateRepository.findMaxSequence(prefix) + 1;
        return prefix + String.format("%05d", next);
    }

    private User requireCurrentUser() {
        User u = currentUser();
        if (u == null) throw new IllegalStateException("Utilisateur non authentifié");
        return u;
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return userRepository.findByUsername(auth.getName()).orElse(null);
    }

    // ── DTO ──────────────────────────────────────────────────────────────────────
    public MedicalCertificateDto toDto(MedicalCertificate c) {
        MedicalCertificateDto dto = new MedicalCertificateDto();
        dto.setId(c.getId());
        dto.setCertificateNumber(c.getCertificateNumber());
        dto.setType(c.getType());
        dto.setIssueDate(c.getIssueDate());
        dto.setRestStartDate(c.getRestStartDate());
        dto.setRestEndDate(c.getRestEndDate());
        dto.setRestDays(c.getRestDays());
        dto.setContent(c.getContent());
        dto.setCreatedAt(c.getCreatedAt());
        dto.setConsultationId(c.getConsultation() != null ? c.getConsultation().getId() : null);
        if (c.getPatient() != null) {
            dto.setPatientId(c.getPatient().getId());
            dto.setPatientName(c.getPatient().getFullName());
            dto.setPatientRecordNumber(c.getPatient().getRecordNumber());
        }
        if (c.getDoctor() != null) {
            dto.setDoctorId(c.getDoctor().getId());
            dto.setDoctorName(c.getDoctor().getFullName());
        }
        return dto;
    }
}
