package com.clinic.backend.billing;

/** Signature HMAC du webhook absente ou invalide → 401 (P3.3). */
public class InvalidWebhookSignatureException extends RuntimeException {
    public InvalidWebhookSignatureException(String message) {
        super(message);
    }
}
