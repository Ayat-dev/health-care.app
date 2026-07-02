package com.clinic.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** KPIs de pilotage pour la direction (tableau de bord administrateur). */
@Getter @Setter
public class AdminDashboardDto {

    // ── Période analysée (mois/année ; sélecteur du dashboard) ────────────────
    private int periodMonth;
    private int periodYear;
    /** true si la période sélectionnée est le mois calendaire courant (pilote l'affichage du jour/7j). */
    private boolean currentPeriod;

    // ── Revenus (encaissements) ──────────────────────────────────────────────
    private BigDecimal revenueToday = BigDecimal.ZERO;
    private BigDecimal revenueMonth = BigDecimal.ZERO;
    private BigDecimal revenuePrevMonth = BigDecimal.ZERO;
    /** Variation % du mois courant vs mois précédent (null si mois précédent à 0). */
    private BigDecimal revenueMonthVariationPercent;

    // ── Activité ─────────────────────────────────────────────────────────────
    private long consultationsToday;
    private long consultationsWeek;
    private long consultationsMonth;
    private long patientsTotal;

    // ── Occupation des lits ──────────────────────────────────────────────────
    private long occupiedBeds;
    private long totalBeds;
    private BigDecimal bedOccupancyRate = BigDecimal.ZERO; // %

    // ── Créances (factures impayées échues) ──────────────────────────────────
    private BigDecimal outstandingTotal = BigDecimal.ZERO;
    private long outstandingCount;

    // ── Pharmacie (alertes) ──────────────────────────────────────────────────
    private long lowStockCount;
    private long expiringCount;

    // ── Chiffre d'affaires facturé sur la période (par acte / par service) ────
    private List<LabelValueDto> revenueByAct = new ArrayList<>();
    private List<LabelValueDto> revenueByDepartment = new ArrayList<>();

    // ── Top pathologies (diagnostics les plus fréquents, période) ────────────
    private List<LabelValueDto> topPathologies = new ArrayList<>();

    // ── Répartition des modes de paiement (mois courant) ─────────────────────
    private Map<String, BigDecimal> paymentMethodBreakdown = new LinkedHashMap<>();
}
