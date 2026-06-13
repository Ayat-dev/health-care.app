package com.clinic.backend.appointment;

import com.clinic.backend.department.Department;
import com.clinic.backend.department.DepartmentRepository;
import com.clinic.backend.dto.AppointmentDto;
import com.clinic.backend.model.User;
import com.clinic.backend.notification.NotificationService;
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
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AppointmentService {

    private static final int DEFAULT_DURATION_MIN = 30;
    private static final LocalTime DAY_START = LocalTime.of(7, 0);
    private static final LocalTime DAY_END   = LocalTime.of(20, 0);

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final NotificationService notificationService;

    // ── Listes / recherche ────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<Appointment> search(LocalDate from, LocalDate to,
                                    Long doctorId, Long patientId, String status) {
        LocalDateTime fromTs = from != null ? from.atStartOfDay() : null;
        LocalDateTime toTs   = to   != null ? to.plusDays(1).atStartOfDay() : null;
        return appointmentRepository.search(fromTs, toTs, doctorId, patientId, status);
    }

    @Transactional(readOnly = true)
    public List<Appointment> findForDay(LocalDate day, Long doctorId) {
        return search(day, day, doctorId, null, null);
    }

    @Transactional(readOnly = true)
    public List<Appointment> findForWeek(LocalDate weekStart, Long doctorId) {
        LocalDateTime from = weekStart.atStartOfDay();
        LocalDateTime to   = weekStart.plusDays(7).atStartOfDay();
        return appointmentRepository.search(from, to, doctorId, null, null);
    }

    // ── Détail ────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public Appointment getById(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rendez-vous introuvable : " + id));
    }

    /** Fetch + map in one transaction so lazy patient/doctor/department are available. */
    @Transactional(readOnly = true)
    public AppointmentDto getDtoById(Long id) {
        Appointment a = appointmentRepository.findWithRefsById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rendez-vous introuvable : " + id));
        return toDto(a);
    }

    // ── Création ──────────────────────────────────────────────────────────
    public Appointment create(AppointmentDto dto) {
        Appointment a = new Appointment();
        applyDto(dto, a, null);
        a.setStatus(dto.getStatus() != null && !dto.getStatus().isBlank()
                ? dto.getStatus() : "PLANIFIE");
        a.setCreatedBy(currentUser());
        Appointment saved = appointmentRepository.save(a);
        notificationService.notifyAppointmentCreated(saved); // SMS de confirmation au patient
        return saved;
    }

    // ── Modification ──────────────────────────────────────────────────────
    public Appointment update(Long id, AppointmentDto dto) {
        Appointment a = getById(id);
        applyDto(dto, a, id);
        if (dto.getStatus() != null && !dto.getStatus().isBlank()) {
            a.setStatus(dto.getStatus());
        }
        return appointmentRepository.save(a);
    }

    // ── Transitions de statut ─────────────────────────────────────────────
    public Appointment confirm(Long id)  { return setStatus(id, "CONFIRME"); }
    public Appointment start(Long id)     { return setStatus(id, "EN_COURS"); }
    public Appointment complete(Long id)  { return setStatus(id, "TERMINE"); }
    public Appointment markAbsent(Long id){ return setStatus(id, "ABSENT"); }

    public Appointment cancel(Long id, String reason) {
        Appointment a = getById(id);
        a.setStatus("ANNULE");
        a.setCancelledAt(LocalDateTime.now());
        a.setCancelReason(reason);
        return appointmentRepository.save(a);
    }

    private Appointment setStatus(Long id, String status) {
        Appointment a = getById(id);
        a.setStatus(status);
        return appointmentRepository.save(a);
    }

    // ── Règle métier : chevauchement ──────────────────────────────────────
    @Transactional(readOnly = true)
    public boolean hasConflict(Long doctorId, LocalDateTime start, LocalDateTime end) {
        return hasConflict(doctorId, start, end, null);
    }

    @Transactional(readOnly = true)
    public boolean hasConflict(Long doctorId, LocalDateTime start, LocalDateTime end, Long excludeId) {
        long exclude = excludeId != null ? excludeId : -1L;
        return appointmentRepository.countOverlaps(doctorId, start, end, exclude) > 0;
    }

    // ── Règle métier : créneaux disponibles ───────────────────────────────
    /** 30-minute slots between 07:00 and 20:00 not overlapping an active appointment. */
    @Transactional(readOnly = true)
    public List<LocalTime> getAvailableSlots(Long doctorId, LocalDate date) {
        List<Appointment> taken = appointmentRepository.findActiveForDoctorBetween(
                doctorId, date.atStartOfDay(), date.plusDays(1).atStartOfDay());

        List<LocalTime> slots = new ArrayList<>();
        LocalTime slot = DAY_START;
        while (!slot.plusMinutes(DEFAULT_DURATION_MIN).isAfter(DAY_END)) {
            LocalDateTime slotStart = date.atTime(slot);
            LocalDateTime slotEnd   = slotStart.plusMinutes(DEFAULT_DURATION_MIN);
            boolean free = taken.stream().noneMatch(a ->
                    a.getStartTime().isBefore(slotEnd) && a.getEndTime().isAfter(slotStart));
            if (free) slots.add(slot);
            slot = slot.plusMinutes(DEFAULT_DURATION_MIN);
        }
        return slots;
    }

    // ── Mapping DTO → entité (avec validations métier) ─────────────────────
    private void applyDto(AppointmentDto dto, Appointment a, Long currentId) {
        if (dto.getPatientId() == null) {
            throw new IllegalArgumentException("Le patient est obligatoire");
        }
        if (dto.getDoctorId() == null) {
            throw new IllegalArgumentException("Le médecin est obligatoire");
        }
        if (dto.getStartTime() == null) {
            throw new IllegalArgumentException("La date/heure de début est obligatoire");
        }

        Patient patient = patientRepository.findByIdAndDeletedAtIsNull(dto.getPatientId())
                .orElseThrow(() -> new IllegalArgumentException("Patient introuvable : " + dto.getPatientId()));
        User doctor = userRepository.findById(dto.getDoctorId())
                .orElseThrow(() -> new IllegalArgumentException("Médecin introuvable : " + dto.getDoctorId()));

        LocalDateTime start = dto.getStartTime();
        LocalDateTime end = dto.getEndTime() != null
                ? dto.getEndTime()
                : start.plusMinutes(DEFAULT_DURATION_MIN);
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("L'heure de fin doit être après l'heure de début");
        }

        // Pas de RDV dans le passé, sauf ADMIN
        if (start.isBefore(LocalDateTime.now()) && !isCurrentUserAdmin()) {
            throw new IllegalArgumentException("Impossible de créer un rendez-vous dans le passé");
        }

        // Pas de double réservation pour le médecin
        if (hasConflict(doctor.getId(), start, end, currentId)) {
            throw new IllegalArgumentException(
                    "Ce médecin a déjà un rendez-vous sur ce créneau");
        }

        a.setPatient(patient);
        a.setDoctor(doctor);
        if (dto.getDepartmentId() != null) {
            Department dep = departmentRepository.findById(dto.getDepartmentId())
                    .orElseThrow(() -> new IllegalArgumentException("Département introuvable : " + dto.getDepartmentId()));
            a.setDepartment(dep);
        } else {
            a.setDepartment(null);
        }
        a.setStartTime(start);
        a.setEndTime(end);
        a.setType(dto.getType());
        a.setReason(dto.getReason());
        a.setNotes(dto.getNotes());
    }

    // ── Utilitaires sécurité ──────────────────────────────────────────────
    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        return userRepository.findByUsername(auth.getName()).orElse(null);
    }

    private boolean isCurrentUserAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(g -> "ROLE_ADMIN".equals(g.getAuthority()));
    }

    // ── DTO depuis entité (l'entité doit avoir ses associations initialisées) ──
    public AppointmentDto toDto(Appointment a) {
        AppointmentDto dto = new AppointmentDto();
        dto.setId(a.getId());
        dto.setPatientId(a.getPatient() != null ? a.getPatient().getId() : null);
        dto.setDoctorId(a.getDoctor() != null ? a.getDoctor().getId() : null);
        dto.setDepartmentId(a.getDepartment() != null ? a.getDepartment().getId() : null);
        dto.setStartTime(a.getStartTime());
        dto.setEndTime(a.getEndTime());
        dto.setStatus(a.getStatus());
        dto.setType(a.getType());
        dto.setReason(a.getReason());
        dto.setNotes(a.getNotes());
        dto.setPatientName(a.getPatient() != null ? a.getPatient().getFullName() : null);
        dto.setDoctorName(a.getDoctor() != null ? a.getDoctor().getFullName() : null);
        dto.setDepartmentName(a.getDepartment() != null ? a.getDepartment().getName() : null);
        return dto;
    }
}
