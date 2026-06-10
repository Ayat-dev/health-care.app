package com.clinic.backend.controller.api;

import com.clinic.backend.dto.*;
import com.clinic.backend.pharmacy.Dispensation;
import com.clinic.backend.pharmacy.Drug;
import com.clinic.backend.pharmacy.PharmacyService;
import com.clinic.backend.pharmacy.StockItem;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pharmacy")
@RequiredArgsConstructor
public class PharmacyApiController {

    private final PharmacyService pharmacyService;

    // ── Catalogue médicaments ────────────────────────────────────────────────
    @GetMapping("/drugs")
    public List<DrugDto> drugs(@RequestParam(required = false) String q,
                               @RequestParam(required = false) String category) {
        return pharmacyService.listDrugs(q, category);
    }

    @PostMapping("/drugs")
    @PreAuthorize("hasAnyRole('PHARMACIEN','ADMIN')")
    public ResponseEntity<DrugDto> createDrug(@RequestBody DrugDto dto) {
        Drug created = pharmacyService.createDrug(dto);
        return ResponseEntity.ok(pharmacyService.toDrugDto(created));
    }

    @PutMapping("/drugs/{id}")
    @PreAuthorize("hasAnyRole('PHARMACIEN','ADMIN')")
    public DrugDto updateDrug(@PathVariable Long id, @RequestBody DrugDto dto) {
        return pharmacyService.toDrugDto(pharmacyService.updateDrug(id, dto));
    }

    // ── Stock ────────────────────────────────────────────────────────────────
    @GetMapping("/stock")
    public List<StockItemDto> stock() {
        return pharmacyService.listStock();
    }

    @PostMapping("/stock/receive")
    @PreAuthorize("hasAnyRole('PHARMACIEN','ADMIN')")
    public ResponseEntity<StockItemDto> receive(@RequestBody StockItemDto dto) {
        StockItem received = pharmacyService.receiveStock(dto);
        return ResponseEntity.ok(pharmacyService.toStockDto(received));
    }

    @GetMapping("/stock/expiring")
    public List<StockItemDto> expiring() {
        return pharmacyService.expiringStock();
    }

    @GetMapping("/stock/low")
    public List<StockItemDto> low() {
        return pharmacyService.lowStock();
    }

    // ── Dispensations ──────────────────────────────────────────────────────────
    @PostMapping("/dispensations")
    @PreAuthorize("hasAnyRole('PHARMACIEN','ADMIN')")
    public ResponseEntity<DispensationDto> dispense(@RequestBody DispensationDto dto) {
        Dispensation d = pharmacyService.dispense(dto);
        return ResponseEntity.ok(pharmacyService.getDispensationDto(d.getId()));
    }

    @GetMapping("/dispensations")
    public List<DispensationDto> dispensations() {
        return pharmacyService.listDispensations();
    }

    @GetMapping("/dispensations/{id}")
    public DispensationDto dispensation(@PathVariable Long id) {
        return pharmacyService.getDispensationDto(id);
    }
}
