package com.clinic.backend.controller.api;

import com.clinic.backend.department.Department;
import com.clinic.backend.department.DepartmentService;
import com.clinic.backend.dto.DepartmentDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
public class DepartmentApiController {

    private final DepartmentService departmentService;

    @GetMapping
    public List<DepartmentDto> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        List<Department> departments = activeOnly
                ? departmentService.listActive()
                : departmentService.listAll();
        return departments.stream().map(departmentService::toDto).toList();
    }

    @GetMapping("/{id}")
    public DepartmentDto get(@PathVariable Long id) {
        return departmentService.toDto(departmentService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DepartmentDto> create(@RequestBody DepartmentDto dto) {
        Department created = departmentService.create(dto);
        return ResponseEntity.ok(departmentService.toDto(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public DepartmentDto update(@PathVariable Long id, @RequestBody DepartmentDto dto) {
        return departmentService.toDto(departmentService.update(id, dto));
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public DepartmentDto toggle(@PathVariable Long id) {
        departmentService.toggleActive(id);
        return departmentService.toDto(departmentService.getById(id));
    }
}
