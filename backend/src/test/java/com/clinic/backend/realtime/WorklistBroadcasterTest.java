package com.clinic.backend.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Diffusion worklist (P5.1 Lot D) : chaque événement part sur le topic qu'il porte, et une
 * panne de broker ne remonte pas vers le métier (déjà committé).
 */
class WorklistBroadcasterTest {

    @Test
    void diffuse_sur_le_topic_porte_par_l_evenement() {
        SimpMessagingTemplate tpl = mock(SimpMessagingTemplate.class);
        WorklistBroadcaster broadcaster = new WorklistBroadcaster(tpl);

        broadcaster.onWorklistChanged(new WorklistChangedEvent(WorklistChannels.PHARMACY, "Nouvelle ordonnance"));

        verify(tpl).convertAndSend(eq(WorklistChannels.PHARMACY), eq((Object) "Nouvelle ordonnance"));
    }

    @Test
    void une_diffusion_qui_echoue_ne_propage_pas() {
        SimpMessagingTemplate tpl = mock(SimpMessagingTemplate.class);
        doThrow(new RuntimeException("broker down")).when(tpl).convertAndSend(any(String.class), any(Object.class));
        WorklistBroadcaster broadcaster = new WorklistBroadcaster(tpl);

        assertThatCode(() ->
                broadcaster.onWorklistChanged(new WorklistChangedEvent(WorklistChannels.LAB, "x")))
                .doesNotThrowAnyException();
    }
}
