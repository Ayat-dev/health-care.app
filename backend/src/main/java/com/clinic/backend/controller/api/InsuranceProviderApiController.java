package com.clinic.backend.controller.api;

import com.clinic.backend.dto.InsuranceProviderDto;
import com.clinic.backend.insurance.InsuranceProvider;
import com.clinic.backend.insurance.InsuranceProviderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/insurance-providers")
@RequiredArgsConstructor
public class InsuranceProviderApiController {

    private final InsuranceProviderService insuranceProviderService;

    @GetMapping
    public List<InsuranceProviderDto> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        List<InsuranceProvider> providers = activeOnly
                ? insuranceProviderService.listActive()
                : insuranceProviderService.listAll();
        return providers.stream().map(insuranceProviderService::toDto).toList();
    }

    @GetMapping("/{id}")
    public InsuranceProviderDto get(@PathVariable Long id) {
        return insuranceProviderService.toDto(insuranceProviderService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InsuranceProviderDto> create(@RequestBody InsuranceProviderDto dto) {
        InsuranceProvider created = insuranceProviderService.create(dto);
        return ResponseEntity.ok(insuranceProviderService.toDto(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public InsuranceProviderDto update(@PathVariable Long id, @RequestBody InsuranceProviderDto dto) {
        return insuranceProviderService.toDto(insuranceProviderService.update(id, dto));
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public InsuranceProviderDto toggle(@PathVariable Long id) {
        insuranceProviderService.toggleActive(id);
        return insuranceProviderService.toDto(insuranceProviderService.getById(id));
    }
}
