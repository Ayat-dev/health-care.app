package com.clinic.backend.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Façade typée (P5.1 Lot D) que les services métier injectent pour signaler un changement
 * de worklist sans dépendre de la couche messaging. Publie un {@link WorklistChangedEvent}
 * dans la transaction courante ; la diffusion STOMP a lieu après commit ({@link WorklistBroadcaster}).
 */
@Component
@RequiredArgsConstructor
public class WorklistEvents {

    private final ApplicationEventPublisher publisher;

    public void labChanged(String summary) {
        publisher.publishEvent(new WorklistChangedEvent(WorklistChannels.LAB, summary));
    }

    public void radiologyChanged(String summary) {
        publisher.publishEvent(new WorklistChangedEvent(WorklistChannels.RADIOLOGY, summary));
    }

    public void pharmacyChanged(String summary) {
        publisher.publishEvent(new WorklistChangedEvent(WorklistChannels.PHARMACY, summary));
    }

    public void billingQueueChanged(String summary) {
        publisher.publishEvent(new WorklistChangedEvent(WorklistChannels.BILLING_QUEUE, summary));
    }
}
