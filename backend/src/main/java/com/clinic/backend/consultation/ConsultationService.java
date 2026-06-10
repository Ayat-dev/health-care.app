package com.clinic.backend.consultation;

import com.clinic.backend.appointment.Appointment;
import com.clinic.backend.appointment.AppointmentRepository;
import com.clinic.backend.department.Department;
import com.clinic.backend.department.DepartmentRepository;
import com.clinic.backend.dto.ConsultationDto;
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
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ConsultationService {

    private final ConsultationRepository consultationRepository;
    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;

    // ── Listes / recherche ────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<Consultation> search(LocalDate from, LocalDate to,
                                     Long doctorId, Long patientId, String status) {
        LocalDateTime fromTs = from != null ? from.atStartOfDay() : null;
        LocalDateTime toTs   = to   != null ? to.plusDays(1).atStartOfDay() : null;
        return consultationRepository.search(fromTs, toTs, doctorId, patientId, status);
    }

    @Transactional(readOnly = true)
    public List<Consultation> findForPatient(Long patientId) {
        return consultationRepository.findByPatient(patientId);
    }

    // ── Détail ────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public Consultation getById(Long id) {
        return consultationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Consultation introuvable : " + id));
    }

    /** Fetch + map in one transaction so lazy patient/doctor/department are available. */
    @Transactional(readOnly = true)
    public ConsultationDto getDtoById(Long id) {
        Consultation c = consultationRepository.findWithRefsById(id)
                .orElseThrow(() -> new IllegalArgumentException("Consultation introuvable : " + id));
        return toDto(c);
    }

    /** Prefill a new consultation from an appointment (patient/doctor/department + emergency flag). */
    @Transactional(readOnly = true)
    public ConsultationDto prefillFromAppointment(Long appointmentId) {
        ConsultationDto dto = new ConsultationDto();
        dto.setStatus("EN_COURS");
        dto.setConsultationDate(LocalDateTime.now());
        if (appointmentId != null) {
            Appointment a = appointmentRepository.findWithRefsById(appointmentId)
                    .orElseThrow(() -> new IllegalArgumentException("Rendez-vous introuvable : " + appointmentId));
            dto.setAppointmentId(a.getId());
            dto.setPatientId(a.getPatient() != null ? a.getPatient().getId() : null);
            dto.setDoctorId(a.getDoctor() != null ? a.getDoctor().getId() : null);
            dto.setDepartmentId(a.getDepartment() != null ? a.getDepartment().getId() : null);
            dto.setChiefComplaint(a.getReason());
            dto.setEmergency("URGENCE".equals(a.getType()));
        }
        return dto;
    }

    // ── Création ──────────────────────────────────────────────────────────
    public Consultation create(ConsultationDto dto) {
        Consultation c = new Consultation();
        if (dto.getConsultationDate() != null) {
            c.setConsultationDate(dto.getConsultationDate());
        }
        applyDto(dto, c);
        c.setStatus(dto.getStatus() != null && !dto.getStatus().isBlank()
                ? dto.getStatus() : "EN_COURS");
        if (dto.getAppointmentId() != null) {
            Appointment a = appointmentRepository.findById(dto.getAppointmentId())
                    .orElseThrow(() -> new IllegalArgumentException("Rendez-vous introuvable : " + dto.getAppointmentId()));
            c.setAppointment(a);
        }
        return consultationRepository.save(c);
    }

    // ── Modification ──────────────────────────────────────────────────────
    public Consultation update(Long id, ConsultationDto dto) {
        Consultation c = getById(id);
        // Une consultation clôturée ne peut plus être modifiée (sauf ADMIN)
        if ("TERMINE".equals(c.getStatus()) && !isCurrentUserAdmin()) {
            throw new IllegalStateException("Cette consultation est clôturée et ne peut plus être modifiée");
        }
        if (dto.getConsultationDate() != null) {
            c.setConsultationDate(dto.getConsultationDate());
        }
        applyDto(dto, c);
        if (dto.getStatus() != null && !dto.getStatus().isBlank()) {
            c.setStatus(dto.getStatus());
        }
        return consultationRepository.save(c);
    }

    // ── Clôture ─────────────────────────────────────────────────────────────
    /** EN_COURS → TERMINE. Le diagnostic est obligatoire pour clôturer. */
    public Consultation complete(Long id) {
        Consultation c = getById(id);
        if (c.getDiagnosis() == null || c.getDiagnosis().isBlank()) {
            throw new IllegalArgumentException("Le diagnostic est obligatoire pour clôturer la consultation");
        }
        c.setStatus("TERMINE");
        return consultationRepository.save(c);
    }

    public Consultation cancel(Long id) {
        Consultation c = getById(id);
        c.setStatus("ANNULE");
        return consultationRepository.save(c);
    }

    // ── Mapping DTO → entité ─────────────────────────────────────────────────
    private void applyDto(ConsultationDto dto, Consultation c) {
        if (dto.getPatientId() == null) {
            throw new IllegalArgumentException("Le patient est obligatoire");
        }
        if (dto.getDoctorId() == null) {
            throw new IllegalArgumentException("Le médecin est obligatoire");
        }

        Patient patient = patientRepository.findByIdAndDeletedAtIsNull(dto.getPatientId())
                .orElseThrow(() -> new IllegalArgumentException("Patient introuvable : " + dto.getPatientId()));
        User doctor = userRepository.findById(dto.getDoctorId())
                .orElseThrow(() -> new IllegalArgumentException("Médecin introuvable : " + dto.getDoctorId()));

        c.setPatient(patient);
        c.setDoctor(doctor);
        if (dto.getDepartmentId() != null) {
            Department dep = departmentRepository.findById(dto.getDepartmentId())
                    .orElseThrow(() -> new IllegalArgumentException("Département introuvable : " + dto.getDepartmentId()));
            c.setDepartment(dep);
        } else {
            c.setDepartment(null);
        }

        // Constantes vitales
        c.setWeightKg(dto.getWeightKg());
        c.setHeightCm(dto.getHeightCm());
        c.setTemperatureC(dto.getTemperatureC());
        c.setBpSystolic(dto.getBpSystolic());
        c.setBpDiastolic(dto.getBpDiastolic());
        c.setPulseBpm(dto.getPulseBpm());
        c.setSpo2Percent(dto.getSpo2Percent());
        c.setRespiratoryRate(dto.getRespiratoryRate());

        // Clinique
        c.setChiefComplaint(dto.getChiefComplaint());
        c.setHistory(dto.getHistory());
        c.setPhysicalExam(dto.getPhysicalExam());
        c.setDiagnosis(dto.getDiagnosis());
        c.setIcd10Codes(dto.getIcd10Codes());
        c.setTreatmentPlan(dto.getTreatmentPlan());
        c.setFollowUpDate(dto.getFollowUpDate());
        c.setEmergency(dto.isEmergency());
    }

    // ── Utilitaires sécurité ──────────────────────────────────────────────
    private boolean isCurrentUserAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(g -> "ROLE_ADMIN".equals(g.getAuthority()));
    }

    // ── DTO depuis entité (associations initialisées requises) ────────────────
    public ConsultationDto toDto(Consultation c) {
        ConsultationDto dto = new ConsultationDto();
        dto.setId(c.getId());
        dto.setAppointmentId(c.getAppointment() != null ? c.getAppointment().getId() : null);
        dto.setPatientId(c.getPatient() != null ? c.getPatient().getId() : null);
        dto.setDoctorId(c.getDoctor() != null ? c.getDoctor().getId() : null);
        dto.setDepartmentId(c.getDepartment() != null ? c.getDepartment().getId() : null);
        dto.setConsultationDate(c.getConsultationDate());
        dto.setWeightKg(c.getWeightKg());
        dto.setHeightCm(c.getHeightCm());
        dto.setTemperatureC(c.getTemperatureC());
        dto.setBpSystolic(c.getBpSystolic());
        dto.setBpDiastolic(c.getBpDiastolic());
        dto.setPulseBpm(c.getPulseBpm());
        dto.setSpo2Percent(c.getSpo2Percent());
        dto.setRespiratoryRate(c.getRespiratoryRate());
        dto.setChiefComplaint(c.getChiefComplaint());
        dto.setHistory(c.getHistory());
        dto.setPhysicalExam(c.getPhysicalExam());
        dto.setDiagnosis(c.getDiagnosis());
        dto.setIcd10Codes(c.getIcd10Codes());
        dto.setTreatmentPlan(c.getTreatmentPlan());
        dto.setFollowUpDate(c.getFollowUpDate());
        dto.setEmergency(c.isEmergency());
        dto.setStatus(c.getStatus());
        if (c.getPatient() != null) {
            dto.setPatientName(c.getPatient().getFullName());
            dto.setPatientRecordNumber(c.getPatient().getRecordNumber());
        }
        dto.setDoctorName(c.getDoctor() != null ? c.getDoctor().getFullName() : null);
        dto.setDepartmentName(c.getDepartment() != null ? c.getDepartment().getName() : null);
        return dto;
    }
}
