package com.clinic.backend.hospitalization;

import com.clinic.backend.department.Department;
import com.clinic.backend.department.DepartmentRepository;
import com.clinic.backend.dto.RoomDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class RoomService {

    private final RoomRepository roomRepository;
    private final DepartmentRepository departmentRepository;

    // ── Listes ────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<RoomDto> listAll() {
        return roomRepository.findAllWithDepartment().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<Room> listActive() {
        return roomRepository.findActiveWithDepartment();
    }

    // ── Détail ────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public Room getById(Long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Chambre introuvable : " + id));
    }

    @Transactional(readOnly = true)
    public RoomDto getDtoById(Long id) {
        return toDto(roomRepository.findWithDepartmentById(id)
                .orElseThrow(() -> new IllegalArgumentException("Chambre introuvable : " + id)));
    }

    // ── Création ──────────────────────────────────────────────────────────
    public Room create(RoomDto dto) {
        String number = normalizeNumber(dto.getRoomNumber());
        if (roomRepository.existsByRoomNumberIgnoreCase(number)) {
            throw new IllegalArgumentException("Ce numéro de chambre existe déjà : " + number);
        }
        Room r = new Room();
        r.setRoomNumber(number);
        mapDtoToEntity(dto, r);
        return roomRepository.save(r);
    }

    // ── Modification ──────────────────────────────────────────────────────
    public Room update(Long id, RoomDto dto) {
        Room r = getById(id);
        String number = normalizeNumber(dto.getRoomNumber());
        roomRepository.findByRoomNumberIgnoreCase(number)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new IllegalArgumentException("Ce numéro de chambre existe déjà : " + number);
                });
        r.setRoomNumber(number);
        mapDtoToEntity(dto, r);
        return roomRepository.save(r);
    }

    // ── Activation / désactivation (suppression logique via is_active) ──────
    public void toggleActive(Long id) {
        Room r = getById(id);
        r.setActive(!r.isActive());
        roomRepository.save(r);
    }

    // ── Mapping DTO → entité ──────────────────────────────────────────────
    private void mapDtoToEntity(RoomDto dto, Room r) {
        if (dto.getDepartmentId() != null) {
            Department d = departmentRepository.findById(dto.getDepartmentId())
                    .orElseThrow(() -> new IllegalArgumentException("Département introuvable : " + dto.getDepartmentId()));
            r.setDepartment(d);
        } else {
            r.setDepartment(null);
        }
        r.setType(dto.getType() != null && !dto.getType().isBlank() ? dto.getType() : "STANDARD");
        r.setCapacity(Math.max(1, dto.getCapacity()));
        r.setDailyRate(dto.getDailyRate() != null ? dto.getDailyRate() : BigDecimal.ZERO);
        r.setActive(dto.isActive());
        r.setNotes(dto.getNotes());
    }

    private String normalizeNumber(String number) {
        if (number == null || number.isBlank()) {
            throw new IllegalArgumentException("Le numéro de chambre est obligatoire");
        }
        return number.trim();
    }

    // ── DTO depuis entité (sans occupation — voir HospitalizationService) ──
    public RoomDto toDto(Room r) {
        RoomDto dto = new RoomDto();
        dto.setId(r.getId());
        dto.setRoomNumber(r.getRoomNumber());
        if (r.getDepartment() != null) {
            dto.setDepartmentId(r.getDepartment().getId());
            dto.setDepartmentName(r.getDepartment().getName());
        }
        dto.setType(r.getType());
        dto.setCapacity(r.getCapacity());
        dto.setDailyRate(r.getDailyRate());
        dto.setActive(r.isActive());
        dto.setNotes(r.getNotes());
        return dto;
    }
}
