package com.clinic.backend.realtime;

/**
 * Topics STOMP des worklists temps réel (P5.1 Lot D). Une destination par service ;
 * l'autorisation d'abonnement par rôle est définie dans {@code WebSocketSecurityConfig}.
 */
public final class WorklistChannels {

    public static final String LAB           = "/topic/worklist/lab";
    public static final String RADIOLOGY     = "/topic/worklist/radiology";
    public static final String PHARMACY      = "/topic/worklist/pharmacy";
    public static final String BILLING_QUEUE = "/topic/billing/queue";

    private WorklistChannels() {}
}
