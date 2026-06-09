# Module 12 — Notifications (SMS, Email, In-App)

## Objectif
Envoyer des rappels, alertes et communications automatiques aux patients et au personnel, avec priorité au SMS (plus adapté au contexte africain).

## Canaux

| Canal | Cas d'usage | Fournisseur |
|---|---|---|
| SMS | Rappels RDV, résultats prêts, alertes stock | Africa's Talking / Twilio |
| Email | Rapports, documents PDF, comptes pro | SMTP (Gmail / Mailgun) |
| In-App | Alertes internes (stock faible, résultats) | Base de données + polling |

## Événements déclencheurs

| Événement | Canal | Délai | Destinataire |
|---|---|---|---|
| RDV créé | SMS | Immédiat | Patient |
| Rappel RDV | SMS | J-1 à 18h | Patient |
| Résultat labo disponible | SMS + In-App | Immédiat | Patient + Médecin |
| Stock médicament faible | In-App | Quotidien 8h | Pharmacien |
| Lot médicament périmant | In-App | 30j avant | Pharmacien |
| Facture impayée | SMS | J+7, J+15 | Patient |
| Rapport journalier | Email | 22h | Admin |

## Architecture

```
Event (ex: AppointmentCreated)
  → NotificationService.schedule(notification)
    → notifications table (status: EN_ATTENTE)
      → @Scheduled job (toutes les minutes)
        → SmsSender / EmailSender / InAppSender
          → UPDATE status = ENVOYE ou ECHEC
```

## Templates SMS (exemples)

**Rappel RDV :**
```
[CLINIQUE] Rappel: RDV demain {date} à {heure} avec Dr. {docteur}.
Confirmer: 1, Annuler: 2. Info: {telephone_clinique}
```

**Résultats prêts :**
```
[CLINIQUE] Vos résultats d'analyse sont disponibles.
Venez les retirer ou contactez-nous: {telephone}
```

## Endpoints REST

```
GET  /api/notifications          → liste (userId, status, type)
POST /api/notifications/test-sms → tester l'envoi SMS (ADMIN)
```

## Implémentation Java

**À créer :**
- `notification/entity/Notification.java`
- `notification/service/NotificationService.java`
- `notification/service/SmsService.java` (Africa's Talking SDK)
- `notification/service/EmailService.java` (Spring Mail)
- `notification/scheduler/NotificationScheduler.java`
  - `@Scheduled(cron = "0 * * * * *")` → traiter la file d'attente
  - `@Scheduled(cron = "0 18 * * *")` → rappels RDV du lendemain
  - `@Scheduled(cron = "0 8 * * *")` → alertes stock pharmacie

**Configuration (application.properties) :**
```properties
app.sms.provider=africas_talking
app.sms.api-key=${SMS_API_KEY}
app.sms.username=${SMS_USERNAME}
app.sms.sender-name=CLINIQUE

spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=${MAIL_USERNAME}
spring.mail.password=${MAIL_PASSWORD}
```
