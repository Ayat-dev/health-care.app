package com.clinic.backend.realtime;

/**
 * Événement applicatif (P5.1 Lot D) signalant qu'une worklist a changé. Publié dans la
 * transaction du service métier ; {@link WorklistBroadcaster} le diffuse en STOMP
 * <b>après commit</b> pour ne jamais pousser sur un rollback.
 *
 * @param channel topic STOMP destinataire (cf. {@link WorklistChannels})
 * @param summary libellé court affiché côté client (toast) — <b>aucune donnée PHI</b>
 */
public record WorklistChangedEvent(String channel, String summary) {}
