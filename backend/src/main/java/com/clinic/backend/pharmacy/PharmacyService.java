package com.clinic.backend.pharmacy;

import com.clinic.backend.consultation.Prescription;
import com.clinic.backend.consultation.PrescriptionItem;
import com.clinic.backend.consultation.PrescriptionRepository;
import com.clinic.backend.dto.*;
import com.clinic.backend.model.User;
import com.clinic.backend.patient.Patient;
import com.clinic.backend.patient.PatientRepository;
import com.clinic.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Pharmacy core: drug catalogue, stock receptions, FIFO dispensation, and dashboard
 * aggregates. Stock-out goes through {@link #dispense} which allocates the earliest
 * expiring non-expired batches first and decrements them atomically.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PharmacyService {

    /** Drugs whose expiry falls within this many days count as "expiring soon". */
    public static final int EXPIRY_WINDOW_DAYS = 30;

    private final DrugRepository drugRepository;
    private final StockItemRepository stockItemRepository;
    private final DispensationRepository dispensationRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;

    // ══════════════════════════════ DRUGS ══════════════════════════════════

    @Transactional(readOnly = true)
    public List<DrugDto> listDrugs(String q, String category) {
        return drugRepository.search(q, category).stream().map(this::toDrugDto).toList();
    }

    @Transactional(readOnly = true)
    public List<DrugDto> listActiveDrugs() {
        return drugRepository.findByActiveTrueOrderByNameAsc().stream().map(this::toDrugDto).toList();
    }

    @Transactional(readOnly = true)
    public List<String> drugCategories() {
        return drugRepository.findDistinctCategories();
    }

    @Transactional(readOnly = true)
    public Drug getDrug(Long id) {
        return drugRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Médicament introuvable : " + id));
    }

    @Transactional(readOnly = true)
    public DrugDto getDrugDto(Long id) {
        return toDrugDto(getDrug(id));
    }

    public Drug createDrug(DrugDto dto) {
        Drug d = new Drug();
        applyDrug(dto, d, null);
        return drugRepository.save(d);
    }

    public Drug updateDrug(Long id, DrugDto dto) {
        Drug d = getDrug(id);
        applyDrug(dto, d, id);
        return drugRepository.save(d);
    }

    public void toggleDrug(Long id) {
        Drug d = getDrug(id);
        d.setActive(!d.isActive());
        drugRepository.save(d);
    }

    private void applyDrug(DrugDto dto, Drug d, Long currentId) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new IllegalArgumentException("Le nom du médicament est obligatoire");
        }
        if (dto.getUnit() == null || dto.getUnit().isBlank()) {
            throw new IllegalArgumentException("L'unité est obligatoire");
        }
        String code = dto.getCode() != null && !dto.getCode().isBlank()
                ? dto.getCode().trim().toUpperCase() : null;
        if (code != null) {
            drugRepository.findByCodeIgnoreCase(code)
                    .filter(other -> !other.getId().equals(currentId))
                    .ifPresent(other -> {
                        throw new IllegalArgumentException("Ce code médicament existe déjà : " + other.getCode());
                    });
        }
        d.setCode(code);
        d.setName(dto.getName().trim());
        d.setGenericName(blankToNull(dto.getGenericName()));
        d.setCategory(blankToNull(dto.getCategory()));
        d.setForm(blankToNull(dto.getForm()));
        d.setDosageStrength(blankToNull(dto.getDosageStrength()));
        d.setUnit(dto.getUnit().trim());
        d.setRequiresPrescription(dto.isRequiresPrescription());
        d.setActive(dto.isActive());
        d.setNotes(blankToNull(dto.getNotes()));
    }

    // ══════════════════════════════ STOCK ══════════════════════════════════

    @Transactional(readOnly = true)
    public List<StockItemDto> listStock() {
        return stockItemRepository.findAllWithDrug().stream().map(this::toStockDto).toList();
    }

    @Transactional(readOnly = true)
    public List<StockItemDto> lowStock() {
        return stockItemRepository.findLowStock(LocalDate.now()).stream().map(this::toStockDto).toList();
    }

    @Transactional(readOnly = true)
    public List<StockItemDto> expiringStock() {
        LocalDate today = LocalDate.now();
        return stockItemRepository.findExpiringBetween(today, today.plusDays(EXPIRY_WINDOW_DAYS))
                .stream().map(this::toStockDto).toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal stockValue() {
        BigDecimal v = stockItemRepository.totalStockValue();
        return v != null ? v : BigDecimal.ZERO;
    }

    /** Stock reception: a new batch enters stock (entrée). Recorded against the current user. */
    public StockItem receiveStock(StockItemDto dto) {
        if (dto.getDrugId() == null) {
            throw new IllegalArgumentException("Le médicament est obligatoire");
        }
        if (dto.getExpiryDate() == null) {
            throw new IllegalArgumentException("La date de péremption est obligatoire");
        }
        if (dto.getExpiryDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Impossible de réceptionner un lot déjà périmé");
        }
        if (dto.getQuantity() <= 0) {
            throw new IllegalArgumentException("La quantité réceptionnée doit être positive");
        }
        if (dto.getSellingPrice() == null || dto.getSellingPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Le prix de vente doit être supérieur ou égal à 0");
        }
        Drug drug = getDrug(dto.getDrugId());

        StockItem s = new StockItem();
        s.setDrug(drug);
        s.setBatchNumber(blankToNull(dto.getBatchNumber()));
        s.setExpiryDate(dto.getExpiryDate());
        s.setQuantity(dto.getQuantity());
        s.setQuantityAlert(dto.getQuantityAlert() > 0 ? dto.getQuantityAlert() : 10);
        s.setPurchasePrice(dto.getPurchasePrice());
        s.setSellingPrice(dto.getSellingPrice());
        s.setSupplier(blankToNull(dto.getSupplier()));
        s.setReceivedAt(dto.getReceivedAt() != null ? dto.getReceivedAt() : LocalDate.now());
        s.setReceivedBy(currentUserOrNull());
        return stockItemRepository.save(s);
    }

    // ═══════════════════════════ DISPENSATIONS ══════════════════════════════

    @Transactional(readOnly = true)
    public List<DispensationDto> listDispensations() {
        return dispensationRepository.findAllWithRefs().stream().map(this::toDispensationDto).toList();
    }

    @Transactional(readOnly = true)
    public DispensationDto getDispensationDto(Long id) {
        Dispensation d = dispensationRepository.findWithRefsById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dispensation introuvable : " + id));
        return toDispensationDto(d);
    }

    /** Pre-fill a dispensation from a prescription: patient + drug lines (where the line is in catalogue). */
    @Transactional(readOnly = true)
    public DispensationDto prefillFromPrescription(Long prescriptionId) {
        Prescription p = prescriptionRepository.findWithRefsById(prescriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Ordonnance introuvable : " + prescriptionId));
        if (p.isDispensed()) {
            throw new IllegalStateException("Cette ordonnance a déjà été dispensée");
        }
        DispensationDto dto = new DispensationDto();
        dto.setPrescriptionId(p.getId());
        dto.setPrescriptionNumber(p.getPrescriptionNumber());
        dto.setPatientId(p.getPatient() != null ? p.getPatient().getId() : null);
        for (PrescriptionItem it : p.getItems()) {
            DispensationItemDto line = new DispensationItemDto();
            line.setDrugId(it.getDrugId());                       // may be null (saisie libre)
            line.setDrugName(it.getDrugName());
            line.setQuantity(it.getQuantity() != null ? it.getQuantity() : 1);
            dto.getItems().add(line);
        }
        return dto;
    }

    /**
     * Dispense drugs to a patient, FIFO across batches. A prescription, if given, must not
     * already be dispensed and is flagged dispensed on success. Each requested line decrements
     * one or more batches (earliest expiry first); insufficient or expired-only stock fails.
     */
    public Dispensation dispense(DispensationDto dto) {
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new IllegalArgumentException("Aucun médicament à dispenser");
        }

        Dispensation d = new Dispensation();
        d.setPharmacist(currentUserRequired());
        d.setDispensedAt(LocalDateTime.now());
        d.setNotes(blankToNull(dto.getNotes()));

        Prescription prescription = null;
        if (dto.getPrescriptionId() != null) {
            prescription = prescriptionRepository.findWithRefsById(dto.getPrescriptionId())
                    .orElseThrow(() -> new IllegalArgumentException("Ordonnance introuvable : " + dto.getPrescriptionId()));
            if (prescription.isDispensed()) {
                throw new IllegalStateException("Cette ordonnance a déjà été dispensée");
            }
            d.setPrescription(prescription);
        }

        // Patient: explicit, else inherited from the prescription
        Long patientId = dto.getPatientId();
        if (patientId == null && prescription != null && prescription.getPatient() != null) {
            patientId = prescription.getPatient().getId();
        }
        if (patientId == null) {
            throw new IllegalArgumentException("Le patient est obligatoire");
        }
        Long resolvedPatientId = patientId;
        Patient patient = patientRepository.findByIdAndDeletedAtIsNull(resolvedPatientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient introuvable : " + resolvedPatientId));
        d.setPatient(patient);

        LocalDate today = LocalDate.now();
        BigDecimal total = BigDecimal.ZERO;

        for (DispensationItemDto line : dto.getItems()) {
            if (line == null || line.getDrugId() == null || line.getQuantity() <= 0) {
                continue; // ligne vide / non sélectionnée
            }
            Drug drug = getDrug(line.getDrugId());
            int remaining = line.getQuantity();

            List<StockItem> batches = stockItemRepository.findAvailableForDrug(drug.getId(), today);
            int available = batches.stream().mapToInt(StockItem::getQuantity).sum();
            if (available < remaining) {
                throw new IllegalArgumentException(
                        "Stock insuffisant pour " + drug.getName()
                        + " (demandé " + remaining + ", disponible " + available + ")");
            }

            for (StockItem batch : batches) {
                if (remaining <= 0) break;
                int take = Math.min(remaining, batch.getQuantity());
                batch.setQuantity(batch.getQuantity() - take);
                batch.setUpdatedAt(LocalDateTime.now());

                BigDecimal unit = batch.getSellingPrice();
                BigDecimal lineTotal = unit.multiply(BigDecimal.valueOf(take));

                DispensationItem item = new DispensationItem();
                item.setStockItem(batch);
                item.setQuantity(take);
                item.setUnitPrice(unit);
                item.setTotalPrice(lineTotal);
                d.addItem(item);

                total = total.add(lineTotal);
                remaining -= take;
            }
        }

        if (d.getItems().isEmpty()) {
            throw new IllegalArgumentException("Aucun médicament valide à dispenser");
        }
        d.setTotalAmount(total);

        Dispensation saved = dispensationRepository.save(d);

        if (prescription != null) {
            prescription.setDispensed(true);
            prescription.setDispensedAt(LocalDateTime.now());
            prescriptionRepository.save(prescription);
        }
        return saved;
    }

    // ═══════════════════════════ DASHBOARD ══════════════════════════════════

    @Transactional(readOnly = true)
    public PharmacyDashboardDto dashboard() {
        LocalDate today = LocalDate.now();
        PharmacyDashboardDto dash = new PharmacyDashboardDto();
        dash.setLowStockCount(stockItemRepository.findLowStock(today).size());
        dash.setExpiringCount(stockItemRepository.findExpiringBetween(today, today.plusDays(EXPIRY_WINDOW_DAYS)).size());
        dash.setExpiredCount(stockItemRepository.findExpiringBetween(today.minusYears(50), today.minusDays(1)).size());
        dash.setStockValue(stockValue());

        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        List<Object[]> rows = dispensationRepository.findTopDispensedSince(monthStart);
        for (Object[] r : rows.stream().limit(10).toList()) {
            Long drugId = ((Number) r[0]).longValue();
            String name = (String) r[1];
            long qty = ((Number) r[2]).longValue();
            dash.getTopDispensed().add(new PharmacyDashboardDto.TopDrug(drugId, name, qty));
        }
        return dash;
    }

    // ════════════════════════════ MAPPERS ═══════════════════════════════════

    public DrugDto toDrugDto(Drug d) {
        DrugDto dto = new DrugDto();
        dto.setId(d.getId());
        dto.setCode(d.getCode());
        dto.setName(d.getName());
        dto.setGenericName(d.getGenericName());
        dto.setCategory(d.getCategory());
        dto.setForm(d.getForm());
        dto.setDosageStrength(d.getDosageStrength());
        dto.setUnit(d.getUnit());
        dto.setRequiresPrescription(d.isRequiresPrescription());
        dto.setActive(d.isActive());
        dto.setNotes(d.getNotes());
        dto.setCreatedAt(d.getCreatedAt());
        return dto;
    }

    public StockItemDto toStockDto(StockItem s) {
        StockItemDto dto = new StockItemDto();
        dto.setId(s.getId());
        dto.setBatchNumber(s.getBatchNumber());
        dto.setExpiryDate(s.getExpiryDate());
        dto.setQuantity(s.getQuantity());
        dto.setQuantityAlert(s.getQuantityAlert());
        dto.setPurchasePrice(s.getPurchasePrice());
        dto.setSellingPrice(s.getSellingPrice());
        dto.setSupplier(s.getSupplier());
        dto.setReceivedAt(s.getReceivedAt());
        if (s.getDrug() != null) {
            dto.setDrugId(s.getDrug().getId());
            dto.setDrugName(s.getDrug().getName());
            dto.setDrugForm(s.getDrug().getForm());
            dto.setDrugDosage(s.getDrug().getDosageStrength());
            dto.setDrugUnit(s.getDrug().getUnit());
        }
        dto.setReceivedByName(s.getReceivedBy() != null ? s.getReceivedBy().getFullName() : null);
        LocalDate today = LocalDate.now();
        dto.setLow(s.getQuantity() <= s.getQuantityAlert());
        dto.setExpired(s.getExpiryDate() != null && s.getExpiryDate().isBefore(today));
        dto.setExpiringSoon(s.getExpiryDate() != null
                && !s.getExpiryDate().isBefore(today)
                && !s.getExpiryDate().isAfter(today.plusDays(EXPIRY_WINDOW_DAYS)));
        return dto;
    }

    public DispensationDto toDispensationDto(Dispensation d) {
        DispensationDto dto = new DispensationDto();
        dto.setId(d.getId());
        dto.setDispensedAt(d.getDispensedAt());
        dto.setTotalAmount(d.getTotalAmount());
        dto.setNotes(d.getNotes());
        if (d.getPrescription() != null) {
            dto.setPrescriptionId(d.getPrescription().getId());
            dto.setPrescriptionNumber(d.getPrescription().getPrescriptionNumber());
        }
        if (d.getPatient() != null) {
            dto.setPatientId(d.getPatient().getId());
            dto.setPatientName(d.getPatient().getFullName());
            dto.setPatientRecordNumber(d.getPatient().getRecordNumber());
        }
        dto.setPharmacistName(d.getPharmacist() != null ? d.getPharmacist().getFullName() : null);
        for (DispensationItem it : d.getItems()) {
            DispensationItemDto idto = new DispensationItemDto();
            idto.setId(it.getId());
            idto.setQuantity(it.getQuantity());
            idto.setUnitPrice(it.getUnitPrice());
            idto.setTotalPrice(it.getTotalPrice());
            if (it.getStockItem() != null) {
                idto.setStockItemId(it.getStockItem().getId());
                idto.setBatchNumber(it.getStockItem().getBatchNumber());
                if (it.getStockItem().getDrug() != null) {
                    idto.setDrugId(it.getStockItem().getDrug().getId());
                    idto.setDrugName(it.getStockItem().getDrug().getName());
                }
            }
            dto.getItems().add(idto);
        }
        return dto;
    }

    // ════════════════════════════ HELPERS ═══════════════════════════════════

    private User currentUserRequired() {
        User u = currentUserOrNull();
        if (u == null) {
            throw new IllegalStateException("Utilisateur courant introuvable pour la dispensation");
        }
        return u;
    }

    private User currentUserOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return userRepository.findByUsername(auth.getName()).orElse(null);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
