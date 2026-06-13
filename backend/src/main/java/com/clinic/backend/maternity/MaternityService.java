package com.clinic.backend.maternity;

import com.clinic.backend.dto.MaternityRecordDto;
import com.clinic.backend.dto.PrenatalVisitDto;
import com.clinic.backend.model.User;
import com.clinic.backend.patient.Patient;
import com.clinic.backend.patient.PatientRepository;
import com.clinic.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class MaternityService {

    private final MaternityRecordRepository recordRepository;
    private final PrenatalVisitRepository visitRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final MaternityRiskCalculator riskCalculator;

    // ── Listes / recherche ────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<MaternityRecordDto> search(String status) {
        return recordRepository.search(status).stream().map(this::toHeaderDto).toList();
    }

    @Transactional(readOnly = true)
    public boolean hasRecord(Long patientId) {
        return recordRepository.existsByPatientId(patientId);
    }

    /** The patiente's dossier mapped to a DTO, or null if she has none (for the patient dossier tab). */
    @Transactional(readOnly = true)
    public MaternityRecordDto findForPatient(Long patientId) {
        return recordRepository.findByPatientId(patientId).map(this::toDto).orElse(null);
    }

    // ── Détail ────────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public MaternityRecord getById(Long id) {
        return recordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dossier maternité introuvable : " + id));
    }

    @Transactional(readOnly = true)
    public MaternityRecordDto getDtoById(Long id) {
        MaternityRecord r = recordRepository.findWithRefsById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dossier maternité introuvable : " + id));
        return toDto(r);
    }

    /** Prefill an "open dossier" form for a given patiente. */
    @Transactional(readOnly = true)
    public MaternityRecordDto prefillForPatient(Long patientId) {
        MaternityRecordDto dto = new MaternityRecordDto();
        dto.setPatientId(patientId);
        if (patientId != null) {
            Patient p = patientRepository.findByIdAndDeletedAtIsNull(patientId).orElse(null);
            if (p != null && p.getAssignedDoctor() != null) {
                dto.setDoctorId(p.getAssignedDoctor().getId());
            }
        }
        return dto;
    }

    // ── Ouverture du dossier ────────────────────────────────────────────────────
    public MaternityRecord openRecord(MaternityRecordDto dto) {
        if (dto.getPatientId() == null) {
            throw new IllegalArgumentException("La patiente est obligatoire");
        }
        if (recordRepository.existsByPatientId(dto.getPatientId())) {
            throw new IllegalStateException("Un dossier maternité existe déjà pour cette patiente");
        }
        Patient patient = patientRepository.findByIdAndDeletedAtIsNull(dto.getPatientId())
                .orElseThrow(() -> new IllegalArgumentException("Patiente introuvable : " + dto.getPatientId()));
        if (!"F".equalsIgnoreCase(patient.getGender())) {
            throw new IllegalStateException("Le dossier maternité ne concerne que les patientes");
        }

        MaternityRecord r = new MaternityRecord();
        r.setPatient(patient);
        applyEditableFields(r, dto);
        r.setStatus("EN_COURS");
        return recordRepository.save(r);
    }

    // ── Mise à jour ──────────────────────────────────────────────────────────────
    public MaternityRecord update(Long id, MaternityRecordDto dto) {
        MaternityRecord r = recordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dossier maternité introuvable : " + id));
        applyEditableFields(r, dto);
        return recordRepository.save(r);
    }

    /** Common base data (used by both open + update). Does not touch status/delivery. */
    private void applyEditableFields(MaternityRecord r, MaternityRecordDto dto) {
        r.setDoctor(resolveDoctor(dto.getDoctorId()));
        r.setGravidity(dto.getGravidity());
        r.setParity(dto.getParity());
        r.setLastPeriodDate(dto.getLastPeriodDate());
        // DPA recalculée à partir des dernières règles (LMP + 280 jours) si non fournie.
        if (dto.getExpectedDueDate() != null) {
            r.setExpectedDueDate(dto.getExpectedDueDate());
        } else if (dto.getLastPeriodDate() != null) {
            r.setExpectedDueDate(dto.getLastPeriodDate().plusDays(280));
        } else {
            r.setExpectedDueDate(null);
        }
        r.setNotes(dto.getNotes());
    }

    // ── Accouchement ─────────────────────────────────────────────────────────────
    public MaternityRecord recordDelivery(Long id, MaternityRecordDto dto) {
        MaternityRecord r = recordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dossier maternité introuvable : " + id));
        if ("CLOTURE".equals(r.getStatus())) {
            throw new IllegalStateException("Ce dossier est clôturé et ne peut plus être modifié");
        }
        if (dto.getDeliveryDate() == null) {
            throw new IllegalArgumentException("La date d'accouchement est obligatoire");
        }
        r.setDeliveryDate(dto.getDeliveryDate());
        r.setDeliveryType(dto.getDeliveryType());
        r.setDeliveryOutcome(dto.getDeliveryOutcome());
        r.setNewbornWeightG(dto.getNewbornWeightG());
        r.setNewbornApgar1(dto.getNewbornApgar1());
        r.setNewbornApgar5(dto.getNewbornApgar5());
        r.setNewbornGender(dto.getNewbornGender());
        r.setComplications(dto.getComplications());
        r.setStatus("ACCOUCHEE");
        return recordRepository.save(r);
    }

    /** ACCOUCHEE → CLOTURE : suivi post-partum terminé. */
    public MaternityRecord close(Long id) {
        MaternityRecord r = getById(id);
        if (!"ACCOUCHEE".equals(r.getStatus())) {
            throw new IllegalStateException("Seul un dossier après accouchement peut être clôturé");
        }
        r.setStatus("CLOTURE");
        return recordRepository.save(r);
    }

    // ── Consultations prénatales (CPN) ───────────────────────────────────────────
    /** Prefill a new CPN form: suggests the next visit number + gestational age from LMP. */
    @Transactional(readOnly = true)
    public PrenatalVisitDto prefillVisit(Long recordId) {
        MaternityRecord r = recordRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("Dossier maternité introuvable : " + recordId));
        PrenatalVisitDto dto = new PrenatalVisitDto();
        dto.setMaternityRecordId(recordId);
        dto.setVisitDate(LocalDate.now());
        dto.setVisitNumber(visitRepository.findMaxVisitNumber(recordId) + 1);
        dto.setGestationalAgeWeeks(riskCalculator.gestationalAgeWeeks(r.getLastPeriodDate(), LocalDate.now()));
        if (r.getDoctor() != null) dto.setDoctorId(r.getDoctor().getId());
        return dto;
    }

    @Transactional(readOnly = true)
    public PrenatalVisitDto getVisitDto(Long visitId) {
        PrenatalVisit v = visitRepository.findWithRefsById(visitId)
                .orElseThrow(() -> new IllegalArgumentException("Consultation prénatale introuvable : " + visitId));
        return toVisitDto(v);
    }

    public PrenatalVisit addVisit(Long recordId, PrenatalVisitDto dto) {
        MaternityRecord r = recordRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("Dossier maternité introuvable : " + recordId));
        if ("CLOTURE".equals(r.getStatus())) {
            throw new IllegalStateException("Ce dossier est clôturé : aucune nouvelle CPN possible");
        }
        if (dto.getVisitDate() == null) {
            throw new IllegalArgumentException("La date de la consultation est obligatoire");
        }
        PrenatalVisit v = new PrenatalVisit();
        applyVisitFields(v, dto, r);
        if (v.getVisitNumber() == null) {
            v.setVisitNumber(visitRepository.findMaxVisitNumber(recordId) + 1);
        }
        r.addVisit(v);
        recordRepository.save(r);
        return v;
    }

    public PrenatalVisit updateVisit(Long visitId, PrenatalVisitDto dto) {
        PrenatalVisit v = visitRepository.findWithRefsById(visitId)
                .orElseThrow(() -> new IllegalArgumentException("Consultation prénatale introuvable : " + visitId));
        if (dto.getVisitDate() == null) {
            throw new IllegalArgumentException("La date de la consultation est obligatoire");
        }
        applyVisitFields(v, dto, v.getMaternityRecord());
        return visitRepository.save(v);
    }

    private void applyVisitFields(PrenatalVisit v, PrenatalVisitDto dto, MaternityRecord record) {
        v.setVisitDate(dto.getVisitDate());
        v.setVisitNumber(dto.getVisitNumber());
        Integer gest = dto.getGestationalAgeWeeks();
        if (gest == null && record != null) {
            gest = riskCalculator.gestationalAgeWeeks(record.getLastPeriodDate(), dto.getVisitDate());
        }
        v.setGestationalAgeWeeks(gest);
        v.setWeightKg(dto.getWeightKg());
        v.setBpSystolic(dto.getBpSystolic());
        v.setBpDiastolic(dto.getBpDiastolic());
        v.setFetalHeartRate(dto.getFetalHeartRate());
        v.setUterineHeightCm(dto.getUterineHeightCm());
        v.setPresentation(dto.getPresentation());
        v.setEdema(dto.getEdema());
        v.setProteinuria(dto.getProteinuria());
        v.setIronSupplemented(dto.getIronSupplemented());
        v.setTtvVaccine(dto.getTtvVaccine());
        v.setObservations(dto.getObservations());
        v.setRecommendations(dto.getRecommendations());
        v.setNextVisitDate(dto.getNextVisitDate());
        v.setDoctor(resolveDoctor(dto.getDoctorId()));
        if (v.getDoctor() == null) v.setDoctor(currentUser());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────
    private User resolveDoctor(Long doctorId) {
        if (doctorId == null) return null;
        return userRepository.findById(doctorId)
                .orElseThrow(() -> new IllegalArgumentException("Médecin introuvable : " + doctorId));
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return userRepository.findByUsername(auth.getName()).orElse(null);
    }

    // ── Mapping DTO ────────────────────────────────────────────────────────────────
    /** Light DTO for list rows (patient/doctor must be initialized; visits not walked). */
    private MaternityRecordDto toHeaderDto(MaternityRecord r) {
        MaternityRecordDto dto = new MaternityRecordDto();
        dto.setId(r.getId());
        dto.setStatus(r.getStatus());
        dto.setGravidity(r.getGravidity());
        dto.setParity(r.getParity());
        dto.setLastPeriodDate(r.getLastPeriodDate());
        dto.setExpectedDueDate(r.getExpectedDueDate());
        dto.setDeliveryDate(r.getDeliveryDate());
        if (r.getPatient() != null) {
            dto.setPatientId(r.getPatient().getId());
            dto.setPatientName(r.getPatient().getFullName());
            dto.setPatientRecordNumber(r.getPatient().getRecordNumber());
        }
        if (r.getDoctor() != null) {
            dto.setDoctorId(r.getDoctor().getId());
            dto.setDoctorName(r.getDoctor().getFullName());
        }
        if (!"ACCOUCHEE".equals(r.getStatus()) && !"CLOTURE".equals(r.getStatus())) {
            dto.setCurrentGestationalAgeWeeks(
                    riskCalculator.gestationalAgeWeeks(r.getLastPeriodDate(), LocalDate.now()));
        }
        return dto;
    }

    /** Full DTO (associations + visits must be initialized). */
    public MaternityRecordDto toDto(MaternityRecord r) {
        MaternityRecordDto dto = toHeaderDto(r);
        dto.setDeliveryType(r.getDeliveryType());
        dto.setDeliveryOutcome(r.getDeliveryOutcome());
        dto.setNewbornWeightG(r.getNewbornWeightG());
        dto.setNewbornApgar1(r.getNewbornApgar1());
        dto.setNewbornApgar5(r.getNewbornApgar5());
        dto.setNewbornGender(r.getNewbornGender());
        dto.setComplications(r.getComplications());
        dto.setNotes(r.getNotes());

        for (PrenatalVisit v : r.getVisits()) {
            dto.getVisits().add(toVisitDto(v));
        }
        dto.setCompletedVisits(r.getVisits().size());
        dto.setAlerts(riskCalculator.evaluate(r.getVisits(), dto.getCurrentGestationalAgeWeeks()));
        return dto;
    }

    public PrenatalVisitDto toVisitDto(PrenatalVisit v) {
        PrenatalVisitDto dto = new PrenatalVisitDto();
        dto.setId(v.getId());
        dto.setMaternityRecordId(v.getMaternityRecord() != null ? v.getMaternityRecord().getId() : null);
        dto.setVisitDate(v.getVisitDate());
        dto.setVisitNumber(v.getVisitNumber());
        dto.setGestationalAgeWeeks(v.getGestationalAgeWeeks());
        dto.setWeightKg(v.getWeightKg());
        dto.setBpSystolic(v.getBpSystolic());
        dto.setBpDiastolic(v.getBpDiastolic());
        dto.setFetalHeartRate(v.getFetalHeartRate());
        dto.setUterineHeightCm(v.getUterineHeightCm());
        dto.setPresentation(v.getPresentation());
        dto.setEdema(v.getEdema());
        dto.setProteinuria(v.getProteinuria());
        dto.setIronSupplemented(v.getIronSupplemented());
        dto.setTtvVaccine(v.getTtvVaccine());
        dto.setObservations(v.getObservations());
        dto.setRecommendations(v.getRecommendations());
        dto.setNextVisitDate(v.getNextVisitDate());
        if (v.getDoctor() != null) {
            dto.setDoctorId(v.getDoctor().getId());
            dto.setDoctorName(v.getDoctor().getFullName());
        }
        return dto;
    }
}
