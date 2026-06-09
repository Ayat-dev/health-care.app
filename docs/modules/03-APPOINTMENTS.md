# Module 03 — Rendez-vous & Agenda

## Objectif
Gérer la prise de rendez-vous, l'agenda des médecins, et les rappels automatiques.

## Fonctionnalités
- Création/modification/annulation de RDV
- Vue agenda jour/semaine par médecin
- Gestion des plages horaires disponibles
- Rappels SMS/email automatiques (J-1)
- Gestion des absences (patient absent = statut ABSENT)
- Types de RDV : consultation, suivi, urgence, téléconsultation

## Endpoints REST

```
GET    /api/appointments                  → liste (paramètres: date, doctorId, patientId, status)
GET    /api/appointments/{id}             → détail
POST   /api/appointments                  → créer
PUT    /api/appointments/{id}             → modifier
PATCH  /api/appointments/{id}/confirm     → confirmer
PATCH  /api/appointments/{id}/cancel      → annuler (body: { reason })
PATCH  /api/appointments/{id}/start       → démarrer → statut EN_COURS
PATCH  /api/appointments/{id}/complete    → terminer → statut TERMINE
GET    /api/appointments/slots            → créneaux disponibles (doctorId, date)
```

## Routes web

```
GET      /appointments              → agenda du jour (vue liste)
GET      /appointments/week         → vue semaine
GET/POST /appointments/new          → nouveau RDV
GET/POST /appointments/{id}/edit    → modifier un RDV
```

## Règles métier
- Pas de double réservation pour un médecin (vérification chevauchement)
- Durée par défaut : 30 minutes (configurable par médecin)
- Un RDV TERMINE → propose automatiquement de créer une consultation
- Rappel SMS envoyé la veille à 18h (via module Notifications)
- Un RDV ne peut pas être créé dans le passé (sauf ADMIN)

## Agenda — Vue semaine
Afficher les RDV sous forme de tableau horaire :
- Colonnes : jours de la semaine
- Lignes : créneaux de 30 minutes (7h → 20h)
- Couleur par statut (planifié=bleu, confirmé=vert, urgent=rouge)
- Filtre par médecin

## Implémentation Java

**Existant :** `entity/Appointment.java`, `service/AppointmentService.java`, `controller/api/AppointmentApiController.java`

**À créer :**
- `AppointmentService.getAvailableSlots(Long doctorId, LocalDate date)` → List<LocalTime>
- `AppointmentService.hasConflict(Long doctorId, LocalDateTime start, LocalDateTime end)` → boolean
- Vue semaine dans `AppointmentWebController`
