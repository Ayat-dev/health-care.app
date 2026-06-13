package com.clinic.backend.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * Encaissements d'une période [from, to) pour le rapport de caisse, ordonnés par heure.
     * Les associations invoice/patient/cashier sont chargées paresseusement lors du mapping
     * DTO, qui s'exécute dans la transaction du service (OSIV désactivé).
     */
    List<Payment> findByPaidAtGreaterThanEqualAndPaidAtLessThanOrderByPaidAtAsc(
            LocalDateTime from, LocalDateTime to);
}
