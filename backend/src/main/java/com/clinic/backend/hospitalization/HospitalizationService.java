package com.clinic.backend.hospitalization;

import com.clinic.backend.consultation.Consultation;
import com.clinic.backend.consultation.ConsultationRepository;
import com.clinic.backend.dto.BedBoardDepartmentDto;
import com.clinic.backend.dto.HospitalizationDto;
import com.clinic.backend.dto.RoomDto;
import com.clinic.backend.model.User;
import com.clinic.backend.patient.Patient;
import com.clinic.backend.patient.PatientRepository;
import com.clinic.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class HospitalizationService {

    private final HospitalizationRepository hospitalizationRepository;
    private final RoomRepository roomRepository;
    private final RoomService roomService;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final ConsultationRepository consultationRepository;

    // ── Plan des lits (bed board) ─────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<BedBoardDepartmentDto> bedBoard() {
        List<Room> rooms = roomRepository.findActiveWithDepartment();

        // Séjours en cours regroupés par chambre.
        Map<Long, List<HospitalizationDto>> byRoom = new LinkedHashMap<>();
        for (Hospitalization h : hospitalizationRepository.findActive()) {
            byRoom.computeIfAbsent(h.getRoom().getId(), k -> new ArrayList<>()).add(toOccupantDto(h));
        }

        Map<Long, BedBoardDepartmentDto> sections = new LinkedHashMap<>();
        for (Room room : rooms) {
            RoomDto rdto = roomService.toDto(room);
            List<HospitalizationDto> occupants = byRoom.getOrDefault(room.getId(), List.of());
            rdto.setOccupants(occupants);
            rdto.setOccupiedCount(occupants.size());
            rdto.setAvailableCount(Math.max(0, room.getCapacity() - occupants.size()));

            Long deptKey = room.getDepartment() != null ? room.getDepartment().getId() : -1L;
            BedBoardDepartmentDto section = sections.computeIfAbsent(deptKey, k -> {
                BedBoardDepartmentDto s = new BedBoardDepartmentDto();
                if (room.getDepartment() != null) {
                    s.setDepartmentId(room.getDepartment().getId());
                    s.setDepartmentName(room.getDepartment().getName());
                    s.setColor(room.getDepartment().getColor());
                } else {
                    s.setDepartmentName("Sans service");
                }
                return s;
            });
            section.getRooms().add(rdto);
        }

        List<BedBoardDepartmentDto> result = new ArrayList<>(sections.values());
        result.sort(Comparator.comparing(s -> s.getDepartmentName() == null ? "" : s.getDepartmentName()));
        return result;
    }

    /** Active rooms with live availability — for the admission form (free beds, full rooms). */
    @Transactional(readOnly = true)
    public List<RoomDto> availableRooms() {
        List<RoomDto> result = new ArrayList<>();
        for (Room room : roomRepository.findActiveWithDepartment()) {
            RoomDto rdto = roomService.toDto(room);
            int occupied = (int) hospitalizationRepository.countByRoomIdAndStatus(room.getId(), "ADMIS");
            rdto.setOccupiedCount(occupied);
            rdto.setAvailableCount(Math.max(0, room.getCapacity() - occupied));
            result.add(rdto);
        }
        return result;
    }

    // ── Listes / recherche ─────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<Hospitalization> search(String status) {
        return hospitalizationRepository.search(status);
    }

    @Transactional(readOnly = true)
    public List<HospitalizationDto> searchDto(String status) {
        return hospitalizationRepository.search(status).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<HospitalizationDto> findForPatient(Long patientId) {
        return hospitalizationRepository.findByPatient(patientId).stream().map(this::toDto).toList();
    }

    // ── Détail ───────────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public Hospitalization getById(Long id) {
        return hospitalizationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Hospitalisation introuvable : " + id));
    }

    @Transactional(readOnly = true)
    public HospitalizationDto getDtoById(Long id) {
        return toDto(hospitalizationRepository.findWithRefsById(id)
                .orElseThrow(() -> new IllegalArgumentException("Hospitalisation introuvable : " + id)));
    }

    /** Prefill a new admission from a patient (and optionally a consultation → doctor). */
    @Transactional(readOnly = true)
    public HospitalizationDto prefillAdmission(Long patientId, Long consultationId, Long roomId) {
        HospitalizationDto dto = new HospitalizationDto();
        if (consultationId != null) {
            Consultation c = consultationRepository.findWithRefsById(consultationId)
                    .orElseThrow(() -> new IllegalArgumentException("Consultation introuvable : " + consultationId));
            if (c.getPatient() != null) dto.setPatientId(c.getPatient().getId());
            if (c.getDoctor() != null) dto.setDoctorId(c.getDoctor().getId());
            if (c.getChiefComplaint() != null) dto.setAdmissionReason(c.getChiefComplaint());
        }
        if (patientId != null) dto.setPatientId(patientId);
        if (roomId != null) dto.setRoomId(roomId);
        return dto;
    }

    // ── Admission ───────────────────────────────────────────────────────────────
    public Hospitalization admit(HospitalizationDto dto) {
        if (dto.getPatientId() == null) throw new IllegalArgumentException("Le patient est obligatoire");
        if (dto.getDoctorId() == null) throw new IllegalArgumentException("Le médecin responsable est obligatoire");
        if (dto.getRoomId() == null) throw new IllegalArgumentException("La chambre est obligatoire");
        if (dto.getAdmissionReason() == null || dto.getAdmissionReason().isBlank()) {
            throw new IllegalArgumentException("Le motif d'admission est obligatoire");
        }
        if (hospitalizationRepository.existsByPatientIdAndStatus(dto.getPatientId(), "ADMIS")) {
            throw new IllegalStateException("Ce patient est déjà hospitalisé");
        }

        Patient patient = patientRepository.findByIdAndDeletedAtIsNull(dto.getPatientId())
                .orElseThrow(() -> new IllegalArgumentException("Patient introuvable : " + dto.getPatientId()));
        User doctor = userRepository.findById(dto.getDoctorId())
                .orElseThrow(() -> new IllegalArgumentException("Médecin introuvable : " + dto.getDoctorId()));
        Room room = roomRepository.findById(dto.getRoomId())
                .orElseThrow(() -> new IllegalArgumentException("Chambre introuvable : " + dto.getRoomId()));
        ensureBedAvailable(room);

        Hospitalization h = new Hospitalization();
        h.setPatient(patient);
        h.setDoctor(doctor);
        h.setRoom(room);
        h.setAdmissionDate(LocalDateTime.now());
        h.setAdmissionReason(dto.getAdmissionReason().trim());
        h.setNotes(dto.getNotes());
        h.setStatus("ADMIS");
        return hospitalizationRepository.save(h);
    }

    // ── Transfert (vers une autre chambre) ────────────────────────────────────────
    /** Closes the current stay as TRANSFERE and opens a fresh ADMIS stay in the new room. */
    public Hospitalization transfer(Long id, Long newRoomId, String reason) {
        Hospitalization current = hospitalizationRepository.findWithRefsById(id)
                .orElseThrow(() -> new IllegalArgumentException("Hospitalisation introuvable : " + id));
        if (!"ADMIS".equals(current.getStatus())) {
            throw new IllegalStateException("Seul un patient admis peut être transféré");
        }
        if (newRoomId == null) throw new IllegalArgumentException("La chambre de destination est obligatoire");
        if (current.getRoom() != null && newRoomId.equals(current.getRoom().getId())) {
            throw new IllegalArgumentException("Le patient est déjà dans cette chambre");
        }
        Room newRoom = roomRepository.findById(newRoomId)
                .orElseThrow(() -> new IllegalArgumentException("Chambre introuvable : " + newRoomId));
        ensureBedAvailable(newRoom);

        String fromRoom = current.getRoom() != null ? current.getRoom().getRoomNumber() : "?";
        current.setStatus("TRANSFERE");
        current.setDischargeDate(LocalDateTime.now());
        current.setUpdatedAt(LocalDateTime.now());
        hospitalizationRepository.save(current);

        Hospitalization next = new Hospitalization();
        next.setPatient(current.getPatient());
        next.setDoctor(current.getDoctor());
        next.setRoom(newRoom);
        next.setAdmissionDate(LocalDateTime.now());
        next.setAdmissionReason(reason != null && !reason.isBlank()
                ? reason.trim() : "Transfert depuis chambre " + fromRoom);
        next.setStatus("ADMIS");
        return hospitalizationRepository.save(next);
    }

    // ── Sortie ─────────────────────────────────────────────────────────────────────
    public Hospitalization discharge(Long id, String status, String diagnosis) {
        Hospitalization h = hospitalizationRepository.findWithRefsById(id)
                .orElseThrow(() -> new IllegalArgumentException("Hospitalisation introuvable : " + id));
        if (!"ADMIS".equals(h.getStatus())) {
            throw new IllegalStateException("Seul un patient admis peut sortir");
        }
        String target = "DECEDE".equals(status) ? "DECEDE" : "SORTI";
        h.setStatus(target);
        h.setDischargeDate(LocalDateTime.now());
        h.setDiagnosisOnDischarge(diagnosis != null && !diagnosis.isBlank() ? diagnosis.trim() : null);
        h.setUpdatedAt(LocalDateTime.now());
        return hospitalizationRepository.save(h);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────
    private void ensureBedAvailable(Room room) {
        if (!room.isActive()) {
            throw new IllegalStateException("La chambre " + room.getRoomNumber() + " est désactivée");
        }
        long occupied = hospitalizationRepository.countByRoomIdAndStatus(room.getId(), "ADMIS");
        if (occupied >= room.getCapacity()) {
            throw new IllegalStateException("La chambre " + room.getRoomNumber() + " est complète");
        }
    }

    private long nightsBetween(LocalDateTime admission, LocalDateTime end) {
        long nights = ChronoUnit.DAYS.between(admission.toLocalDate(), end.toLocalDate());
        return Math.max(1, nights);
    }

    // ── Mapping DTO ───────────────────────────────────────────────────────────────────
    /** Light DTO for a bed-board occupant (patient must be initialized). */
    private HospitalizationDto toOccupantDto(Hospitalization h) {
        HospitalizationDto dto = new HospitalizationDto();
        dto.setId(h.getId());
        dto.setStatus(h.getStatus());
        dto.setAdmissionDate(h.getAdmissionDate());
        if (h.getPatient() != null) {
            dto.setPatientId(h.getPatient().getId());
            dto.setPatientName(h.getPatient().getFullName());
            dto.setPatientRecordNumber(h.getPatient().getRecordNumber());
        }
        dto.setDaysSinceAdmission(ChronoUnit.DAYS.between(h.getAdmissionDate().toLocalDate(),
                LocalDateTime.now().toLocalDate()));
        return dto;
    }

    /** Full DTO (associations must be initialized). */
    public HospitalizationDto toDto(Hospitalization h) {
        HospitalizationDto dto = new HospitalizationDto();
        dto.setId(h.getId());
        dto.setAdmissionDate(h.getAdmissionDate());
        dto.setDischargeDate(h.getDischargeDate());
        dto.setAdmissionReason(h.getAdmissionReason());
        dto.setDiagnosisOnDischarge(h.getDiagnosisOnDischarge());
        dto.setStatus(h.getStatus());
        dto.setNotes(h.getNotes());

        if (h.getPatient() != null) {
            dto.setPatientId(h.getPatient().getId());
            dto.setPatientName(h.getPatient().getFullName());
            dto.setPatientRecordNumber(h.getPatient().getRecordNumber());
        }
        if (h.getDoctor() != null) {
            dto.setDoctorId(h.getDoctor().getId());
            dto.setDoctorName(h.getDoctor().getFullName());
        }
        Room room = h.getRoom();
        if (room != null) {
            dto.setRoomId(room.getId());
            dto.setRoomNumber(room.getRoomNumber());
            dto.setRoomType(room.getType());
            dto.setDailyRate(room.getDailyRate());
            if (room.getDepartment() != null) dto.setDepartmentName(room.getDepartment().getName());
        }

        LocalDateTime end = h.getDischargeDate() != null ? h.getDischargeDate() : LocalDateTime.now();
        dto.setDaysSinceAdmission(ChronoUnit.DAYS.between(h.getAdmissionDate().toLocalDate(), end.toLocalDate()));
        long nights = nightsBetween(h.getAdmissionDate(), end);
        dto.setNights(nights);
        if (room != null && room.getDailyRate() != null) {
            dto.setEstimatedCost(room.getDailyRate().multiply(BigDecimal.valueOf(nights)));
        }
        return dto;
    }
}
