package com.clinic.backend.realtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Diffuse les {@link WorklistChangedEvent} sur leur topic STOMP (P5.1 Lot D).
 *
 * <p><b>Après commit</b> ({@link TransactionPhase#AFTER_COMMIT}) : on ne pousse jamais une
 * mise à jour issue d'une transaction annulée. {@code fallbackExecution=true} diffuse quand
 * même si l'événement est publié hors transaction (robustesse). La diffusion ne lève jamais
 * vers l'appelant — un broker injoignable ne doit pas casser le métier déjà committé.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WorklistBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onWorklistChanged(WorklistChangedEvent event) {
        try {
            messagingTemplate.convertAndSend(event.channel(), event.summary());
        } catch (RuntimeException e) {
            log.warn("Diffusion worklist échouée sur {} : {}", event.channel(), e.getMessage());
        }
    }
}
