package com.clinic.backend.pharmacy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DrugInteractionRepository extends JpaRepository<DrugInteraction, Long> {

    /** Règles d'interaction actives (référence globale, non tenant-scopée). */
    List<DrugInteraction> findByActiveTrue();
}
