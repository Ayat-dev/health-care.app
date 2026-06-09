# Module 02 — Gestion des patients

## Objectif
Gérer le dossier médical complet de chaque patient, de l'admission initiale à l'historique complet des soins.

## Fonctionnalités
- Création et modification du dossier patient
- Recherche multicritères (nom, prénom, N° dossier, téléphone, CNI)
- Historique complet : consultations, ordonnances, labos, hospitalisations, factures
- Gestion des allergies et antécédents
- Photo du patient (upload)
- Lien avec l'assurance / mutuelle

## Numérotation automatique
Format : `{PREFIX}-{ANNEE}-{SÉQUENCE 5 chiffres}`  
Exemple : `PAT-2024-00042`  
Le prefix est configurable dans `clinic_config.patient_record_prefix`.

## Endpoints REST

```
GET    /api/patients               → liste (paramètres: q, page, size, doctorId)
GET    /api/patients/{id}          → détail complet
GET    /api/patients/{id}/history  → historique complet (consultations, labo, factures)
POST   /api/patients               → créer
PUT    /api/patients/{id}          → modifier
DELETE /api/patients/{id}          → suppression logique (ADMIN)
POST   /api/patients/{id}/photo    → upload photo (multipart)
```

## Routes web

```
GET      /patients              → liste avec recherche et pagination
GET      /patients/{id}         → dossier complet du patient (onglets)
GET/POST /patients/new          → formulaire de création
GET/POST /patients/{id}/edit    → formulaire de modification
```

## Vue dossier patient (onglets)
1. **Informations** — données personnelles, contact urgence, assurance
2. **Médical** — groupe sanguin, allergies, antécédents, médecin référent
3. **Consultations** — liste chronologique avec accès aux détails
4. **Ordonnances** — historique des prescriptions
5. **Labo** — résultats d'analyses
6. **Hospitalisations** — séjours passés et en cours
7. **Facturation** — factures et paiements
8. **Documents** — fichiers attachés (PDF, images)

## Règles métier
- Un numéro de dossier est unique et ne peut pas être modifié après création
- La suppression est toujours logique (soft delete via `deleted_at`)
- Un patient ne peut pas être supprimé s'il a une hospitalisation active
- Les champs obligatoires : `first_name`, `last_name`, `phone` (ou `email`)

## Implémentation Java

**Existant :** `entity/Patient.java`, `repository/PatientRepository.java`, `service/PatientService.java`, `controller/api/PatientApiController.java`, `controller/web/PatientWebController.java`

**À enrichir :**
- `PatientService.getFullHistory(Long patientId)` → DTO agrégé
- `PatientService.uploadPhoto(Long patientId, MultipartFile file)`
- Pagination dans `PatientRepository` (extends `JpaRepository` + `PagingAndSortingRepository`)
- Template `patients/detail.html` → onglets (Bootstrap tabs ou CSS pur)
