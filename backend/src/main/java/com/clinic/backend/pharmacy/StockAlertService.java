package com.clinic.backend.pharmacy;

import com.clinic.backend.dto.StockItemDto;
import com.clinic.backend.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Daily stock surveillance. Surfaces low-stock and soon-to-expire batches so they can be
 * acted on (re-order / discard). It logs and — via {@link NotificationService} — raises a
 * STOCK_ALERTE in-app notification to every pharmacist when there is anything to act on.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockAlertService {

    private final PharmacyService pharmacyService;
    private final NotificationService notificationService;

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

        notificationService.notifyStockAlert("Alerte stock pharmacie",
                buildAlertBody(low, expiring));
    }

    private String buildAlertBody(List<StockItemDto> low, List<StockItemDto> expiring) {
        StringBuilder sb = new StringBuilder("Contrôle stock du jour : ")
                .append(low.size()).append(" lot(s) en stock faible, ")
                .append(expiring.size()).append(" lot(s) périmant sous ")
                .append(PharmacyService.EXPIRY_WINDOW_DAYS).append(" jours.");
        low.forEach(s -> sb.append("\n• Stock faible : ").append(s.getDrugName())
                .append(" (lot ").append(s.getBatchNumber()).append(") — ")
                .append(s.getQuantity()).append(" restant(s)."));
        expiring.forEach(s -> sb.append("\n• Péremption : ").append(s.getDrugName())
                .append(" (lot ").append(s.getBatchNumber()).append(") — expire le ")
                .append(s.getExpiryDate()).append("."));
        return sb.toString();
    }
}
