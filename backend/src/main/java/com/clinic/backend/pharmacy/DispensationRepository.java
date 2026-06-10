package com.clinic.backend.pharmacy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DispensationRepository extends JpaRepository<Dispensation, Long> {

    /** History list — patient + pharmacist eagerly fetched, most recent first. */
    @Query("""
        SELECT d FROM Dispensation d
        JOIN FETCH d.patient
        JOIN FETCH d.pharmacist
        LEFT JOIN FETCH d.prescription
        ORDER BY d.dispensedAt DESC
        """)
    List<Dispensation> findAllWithRefs();

    /** Full detail — items + their batch + drug eagerly fetched. */
    @Query("""
        SELECT DISTINCT d FROM Dispensation d
        JOIN FETCH d.patient
        JOIN FETCH d.pharmacist
        LEFT JOIN FETCH d.prescription
        LEFT JOIN FETCH d.items i
        LEFT JOIN FETCH i.stockItem si
        LEFT JOIN FETCH si.drug
        WHERE d.id = :id
        """)
    Optional<Dispensation> findWithRefsById(@Param("id") Long id);

    /**
     * Most-dispensed drugs over a window: [drugId, drugName, totalQuantity], desc.
     * Powers the dashboard "top 10 médicaments dispensés ce mois".
     */
    @Query("""
        SELECT dr.id, dr.name, SUM(i.quantity)
        FROM DispensationItem i
        JOIN i.stockItem si
        JOIN si.drug dr
        JOIN i.dispensation d
        WHERE d.dispensedAt >= :since
        GROUP BY dr.id, dr.name
        ORDER BY SUM(i.quantity) DESC
        """)
    List<Object[]> findTopDispensedSince(@Param("since") LocalDateTime since);
}
