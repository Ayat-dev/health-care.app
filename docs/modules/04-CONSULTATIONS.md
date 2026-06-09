# Module 04 — Consultations & Ordonnances

## Objectif
Documenter chaque acte médical : saisie des constantes vitales, diagnostic, traitement, et génération d'ordonnances imprimables.

## Fonctionnalités
- Saisie complète de la consultation (constantes, anamnèse, examen, diagnostic)
- Codes CIM-10 (classification internationale des maladies)
- Génération d'ordonnances (PDF imprimable, entête clinique)
- Prescription de labos depuis la consultation
- Certificats médicaux, arrêts de travail (PDF)
- Historique chronologique par patient

## Endpoints REST

```
GET    /api/consultations                    → liste (patientId, doctorId, dateFrom, dateTo)
GET    /api/consultations/{id}               → détail
POST   /api/consultations                    → créer (lie à un appointment si fourni)
PUT    /api/consultations/{id}               → modifier
PATCH  /api/consultations/{id}/complete      → clôturer
GET    /api/consultations/{id}/prescription  → prescription liée
POST   /api/consultations/{id}/prescription  → créer une ordonnance
GET    /api/consultations/{id}/pdf           → export PDF de la fiche
GET    /api/prescriptions/{id}/pdf           → ordonnance PDF
```

## Routes web

```
GET      /consultations/new?appointmentId=  → nouvelle consultation (pré-remplie depuis RDV)
GET      /consultations/{id}                → fiche de consultation
GET/POST /consultations/{id}/edit           → modifier
GET      /consultations/{id}/prescription   → créer/voir l'ordonnance
```

## Ordonnance PDF — Structure
```
┌─────────────────────────────────────────┐
│  [LOGO CLINIQUE]    NOM DE LA CLINIQUE  │
│  Adresse · Téléphone                   │
├─────────────────────────────────────────┤
│  Dr. Prénom NOM           Date:         │
│  Spécialité                             │
├─────────────────────────────────────────┤
│  Patient : NOM Prénom      Âge:         │
│  N° Dossier :                           │
├─────────────────────────────────────────┤
│  Rp/                                    │
│  1. MEDICAMENT 500mg                    │
│     3 comprimés/jour pendant 7 jours    │
│  2. MEDICAMENT SIROP                    │
│     2 cuillères/jour pendant 5 jours    │
├─────────────────────────────────────────┤
│  Valable 30 jours · Signature/Cachet    │
└─────────────────────────────────────────┘
```

## Règles métier
- Une consultation = un patient + un médecin + une date
- Le diagnostic est obligatoire pour clôturer
- Une consultation clôturée ne peut plus être modifiée (sauf ADMIN)
- Une ordonnance peut être imprimée en plusieurs exemplaires
- L'ordonnance est automatiquement liée au stock pharmacie pour la dispensation

## Implémentation Java

**À créer :**
- `entity/Consultation.java`
- `entity/Prescription.java` + `PrescriptionItem.java`
- `service/ConsultationService.java`
- `service/PrescriptionService.java`
- `service/PdfGenerationService.java` (iText ou JasperReports)
- `controller/api/ConsultationApiController.java`
- `controller/web/ConsultationWebController.java`
- `templates/consultations/form.html`
- `templates/consultations/detail.html`
- `templates/prescriptions/form.html`
