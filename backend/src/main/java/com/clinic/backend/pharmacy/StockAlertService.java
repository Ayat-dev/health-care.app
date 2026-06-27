package com.clinic.backend.pharmacy;

import com.clinic.backend.dto.StockItemDto;
import com.clinic.backend.notification.NotificationService;
import com.clinic.backend.tenant.Clinic;
import com.clinic.backend.tenant.ClinicRepository;
import com.clinic.backend.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Daily stock surveillance. Surfaces low-stock and soon-to-expire batches so they can be
 * acted on (re-order / discard). It logs and — via {@link NotificationService} — raises a
 * STOCK_ALERTE in-app notification to every pharmacist when there is anything to act on.
 * <p>
 * Multi-tenant (P4.2) : le stock est désormais cloisonné par clinique ({@code @TenantId}).
 * Une tâche de fond tourne <b>sans</b> contexte de requête → tenant sentinelle « fermé »
 * (lectures vides). On itère donc explicitement chaque clinique active via
 * {@link TenantContext#runAs} — patron des schedulers multi-tenant.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockAlertService {

    private final PharmacyService pharmacyService;
    private final NotificationService notificationService;
    private final ClinicRepository clinicRepository;

    /** Every day at 08:00 — flag low stock and upcoming expiries, clinique par clinique. */
    @Scheduled(cron = "0 0 8 * * *")
    public void runDailyCheck() {
        for (Clinic clinic : clinicRepository.findByActiveTrue()) {
            TenantContext.runAs(clinic.getId(), () -> checkClinic(clinic));
        }
    }

    /** Contrôle du stock d'une clinique (exécuté dans son contexte tenant). */
    private void checkClinic(Clinic clinic) {
        List<StockItemDto> low = pharmacyService.lowStock();
        List<StockItemDto> expiring = pharmacyService.expiringStock();

        if (low.isEmpty() && expiring.isEmpty()) {
            log.info("[Pharmacie][{}] Contrôle stock quotidien : aucune alerte.", clinic.getCode());
            return;
        }
        log.warn("[Pharmacie][{}] Contrôle stock quotidien : {} lot(s) en stock faible, {} lot(s) périmant sous {} jours.",
                clinic.getCode(), low.size(), expiring.size(), PharmacyService.EXPIRY_WINDOW_DAYS);
        low.forEach(s -> log.warn("  ▸ Stock faible : {} (lot {}) — {} restant(s), seuil {}",
                s.getDrugName(), s.getBatchNumber(), s.getQuantity(), s.getQuantityAlert()));
        expiring.forEach(s -> log.warn("  ▸ Péremption proche : {} (lot {}) — expire le {}",
                s.getDrugName(), s.getBatchNumber(), s.getExpiryDate()));

        notificationService.notifyStockAlert("Alerte stock pharmacie — " + clinic.getCode(),
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
