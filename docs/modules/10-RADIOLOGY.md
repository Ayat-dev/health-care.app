# Module 10 — Imagerie médicale

## Objectif
Gérer les demandes d'imagerie (radio, écho, scanner), le suivi des examens, et l'archivage des comptes-rendus radiologiques.

## Fonctionnalités
- Demande d'imagerie depuis une consultation
- Types : Radiographie, Échographie, Scanner, IRM, Mammographie
- Saisie du compte-rendu par le radiologue
- Attachement des images (JPEG/PDF)
- Impression du compte-rendu

## Endpoints REST

```
GET    /api/radiology/requests             → liste des demandes
POST   /api/radiology/requests             → créer une demande
GET    /api/radiology/requests/{id}        → détail
POST   /api/radiology/requests/{id}/report → saisir le compte-rendu
POST   /api/radiology/requests/{id}/images → uploader des images
GET    /api/radiology/requests/{id}/pdf    → compte-rendu PDF
```

## Remarque spécifique Afrique
Pour les cliniques n'ayant pas de radiologue sur place : option d'envoi du compte-rendu par téléradiologie (PDF partagé via lien sécurisé). À implémenter en Phase 4.

## Implémentation Java

**À créer :**
- `radiology/entity/RadiologyRequest.java`
- `radiology/entity/RadiologyImage.java`
- `radiology/service/RadiologyService.java`
- Templates : `radiology/list.html`, `radiology/report-form.html`
