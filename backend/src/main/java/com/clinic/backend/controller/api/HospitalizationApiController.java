package com.clinic.backend.controller.api;

import com.clinic.backend.dto.BedBoardDepartmentDto;
import com.clinic.backend.dto.HospitalizationDto;
import com.clinic.backend.dto.RoomDto;
import com.clinic.backend.hospitalization.HospitalizationService;
import com.clinic.backend.hospitalization.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HospitalizationApiController {

    private final HospitalizationService hospitalizationService;
    private final RoomService roomService;

    // ── Plan des lits ──────────────────────────────────────────────────────────
    @GetMapping("/beds")
    public List<BedBoardDepartmentDto> beds() {
        return hospitalizationService.bedBoard();
    }

    // ── Chambres (référentiel) ──────────────────────────────────────────────────
    @GetMapping("/rooms")
    public List<RoomDto> rooms() {
        return roomService.listAll();
    }

    @GetMapping("/rooms/{id}")
    public RoomDto room(@PathVariable Long id) {
        return roomService.getDtoById(id);
    }

    @PostMapping("/rooms")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoomDto> createRoom(@RequestBody RoomDto dto) {
        Long id = roomService.create(dto).getId();
        return ResponseEntity.ok(roomService.getDtoById(id));
    }

    @PutMapping("/rooms/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public RoomDto updateRoom(@PathVariable Long id, @RequestBody RoomDto dto) {
        roomService.update(id, dto);
        return roomService.getDtoById(id);
    }

    @PatchMapping("/rooms/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public RoomDto toggleRoom(@PathVariable Long id) {
        roomService.toggleActive(id);
        return roomService.getDtoById(id);
    }

    // ── Hospitalisations ──────────────────────────────────────────────────────────
    @GetMapping("/hospitalizations")
    public List<HospitalizationDto> list(@RequestParam(required = false) String status) {
        return hospitalizationService.searchDto(status);
    }

    @GetMapping("/hospitalizations/{id}")
    public HospitalizationDto get(@PathVariable Long id) {
        return hospitalizationService.getDtoById(id);
    }

    @PostMapping("/hospitalizations")
    @PreAuthorize("hasAnyRole('MEDECIN','INFIRMIER','ADMIN')")
    public ResponseEntity<HospitalizationDto> admit(@RequestBody HospitalizationDto dto) {
        Long id = hospitalizationService.admit(dto).getId();
        return ResponseEntity.ok(hospitalizationService.getDtoById(id));
    }

    @PatchMapping("/hospitalizations/{id}/transfer")
    @PreAuthorize("hasAnyRole('MEDECIN','INFIRMIER','ADMIN')")
    public HospitalizationDto transfer(@PathVariable Long id,
                                       @RequestParam Long newRoomId,
                                       @RequestParam(required = false) String reason) {
        Long newId = hospitalizationService.transfer(id, newRoomId, reason).getId();
        return hospitalizationService.getDtoById(newId);
    }

    @PatchMapping("/hospitalizations/{id}/discharge")
    @PreAuthorize("hasAnyRole('MEDECIN','INFIRMIER','ADMIN')")
    public HospitalizationDto discharge(@PathVariable Long id,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(required = false) String diagnosis) {
        hospitalizationService.discharge(id, status, diagnosis);
        return hospitalizationService.getDtoById(id);
    }
}
