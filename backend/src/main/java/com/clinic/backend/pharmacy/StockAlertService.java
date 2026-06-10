package com.clinic.backend.pharmacy;

import com.clinic.backend.dto.StockItemDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Daily stock surveillance. Surfaces low-stock and soon-to-expire batches so they can be
 * acted on (re-order / discard). For now it logs; once the Notifications module (12) lands
 * these become STOCK_ALERTE notifications to the pharmacist.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockAlertService {

    private final PharmacyService pharmacyService;

    /** Every day at 08:00 — flag low stock and upcoming expiries. */
    @Scheduled(cron = "0 0 8 * * *")
    public void runDailyCheck() {
        List<StockItemDto> low = pharmacyService.lowStock();
        List<StockItemDto> expiring = pharmacyService.expiringStock();

        if (low.isEmpty() && expiring.isEmpty()) {
            log.info("[Pharmacie] Contrôle stock quotidien : aucune alerte.");
            return;
        }
        log.warn("[Pharmacie] Contrôle stock quotidien : {} lot(s) en stock faible, {} lot(s) périmant sous {} jours.",
                low.size(), expiring.size(), PharmacyService.EXPIRY_WINDOW_DAYS);
        low.forEach(s -> log.warn("  ▸ Stock faible : {} (lot {}) — {} restant(s), seuil {}",
                s.getDrugName(), s.getBatchNumber(), s.getQuantity(), s.getQuantityAlert()));
        expiring.forEach(s -> log.warn("  ▸ Péremption proche : {} (lot {}) — expire le {}",
                s.getDrugName(), s.getBatchNumber(), s.getExpiryDate()));
    }
}
