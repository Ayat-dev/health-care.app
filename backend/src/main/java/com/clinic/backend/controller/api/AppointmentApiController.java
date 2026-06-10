package com.clinic.backend.controller.api;

import com.clinic.backend.appointment.AppointmentService;
import com.clinic.backend.dto.AppointmentDto;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentApiController {

    private final AppointmentService appointmentService;

    @GetMapping
    public List<AppointmentDto> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) String status) {
        LocalDate f = date != null ? date : from;
        LocalDate t = date != null ? date : to;
        return appointmentService.search(f, t, doctorId, patientId, status)
                .stream().map(appointmentService::toDto).toList();
    }

    @GetMapping("/{id}")
    public AppointmentDto get(@PathVariable Long id) {
        return appointmentService.getDtoById(id);
    }

    @GetMapping("/slots")
    public Map<String, Object> slots(
            @RequestParam Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<LocalTime> slots = appointmentService.getAvailableSlots(doctorId, date);
        return Map.of("doctorId", doctorId, "date", date, "slots", slots);
    }

    @PostMapping
    public ResponseEntity<AppointmentDto> create(@RequestBody AppointmentDto dto) {
        Long id = appointmentService.create(dto).getId();
        return ResponseEntity.ok(appointmentService.getDtoById(id));
    }

    @PutMapping("/{id}")
    public AppointmentDto update(@PathVariable Long id, @RequestBody AppointmentDto dto) {
        appointmentService.update(id, dto);
        return appointmentService.getDtoById(id);
    }

    @PatchMapping("/{id}/confirm")
    public AppointmentDto confirm(@PathVariable Long id) {
        appointmentService.confirm(id);
        return appointmentService.getDtoById(id);
    }

    @PatchMapping("/{id}/start")
    public AppointmentDto start(@PathVariable Long id) {
        appointmentService.start(id);
        return appointmentService.getDtoById(id);
    }

    @PatchMapping("/{id}/complete")
    public AppointmentDto complete(@PathVariable Long id) {
        appointmentService.complete(id);
        return appointmentService.getDtoById(id);
    }

    @PatchMapping("/{id}/cancel")
    public AppointmentDto cancel(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        appointmentService.cancel(id, reason);
        return appointmentService.getDtoById(id);
    }
}
