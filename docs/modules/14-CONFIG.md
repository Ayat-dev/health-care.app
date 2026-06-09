# Module 14 — Configuration clinique (Multi-tenant ready)

## Objectif
Permettre d'adapter ClinicApp à n'importe quelle clinique sans toucher au code. Chaque instance est configurée via un fichier `.env` + interface d'administration.

## Niveaux de configuration

### Niveau 1 — Variables d'environnement (`.env`)
Sensibles ou liés à l'infrastructure. Ne jamais dans l'interface :
```env
DB_PASSWORD=...
JWT_SECRET=...
SMS_API_KEY=...
MAIL_PASSWORD=...
```

### Niveau 2 — `application.properties` (par profil)
Comportement technique :
```properties
server.port=8080
app.upload.directory=/data/uploads
app.backup.directory=/data/backups
app.backup.cron=0 2 * * *   # 2h du matin chaque jour
```

### Niveau 3 — `clinic_config` (base de données, interface admin)
Personnalisation métier modifiable sans redémarrer :
- Nom et coordonnées de la clinique
- Logo (upload)
- Devise (XOF, MAD, DZD, EUR, USD…)
- Fuseau horaire
- Modules activés (feature flags)
- Préfixes de numérotation
- Tarifs par défaut des actes

## Interface d'administration (`/admin/config`)

### Onglets
1. **Identité** — nom, logo, adresse, téléphone, email, site web
2. **Modules** — activer/désactiver pharmacie, labo, maternité, dentaire…
3. **Numérotation** — préfixes dossiers, factures, ordonnances
4. **Actes & Tarifs** — catalogue modifiable
5. **Assureurs** — liste des mutuelles/assurances partenaires
6. **Départements** — créer/modifier les services de la clinique
7. **Utilisateurs** — gestion des comptes
8. **Notifications** — tester SMS/email, voir la file d'attente
9. **Sauvegardes** — historique, déclenchement manuel

## Feature Flags — comportement
Si `module_pharmacy = false` :
- Le menu "Pharmacie" disparaît de la navigation
- Les routes `/pharmacy/**` renvoient 403
- Les ordonnances ne proposent pas la dispensation automatique

## Adaptation multi-clinique
Pour déployer pour une nouvelle clinique :
1. Copier `.env.example` → `.env` (remplir les secrets)
2. `docker-compose up -d` (premier démarrage = BDD vide + admin/admin123)
3. Se connecter en admin → onglet Config → remplir les infos de la clinique
4. Créer les utilisateurs, configurer les tarifs, activer les modules

**Temps d'onboarding estimé : 1 à 2 heures.**

## Implémentation Java

**À créer :**
- `config/entity/ClinicConfig.java`
- `config/service/ClinicConfigService.java`
- `config/service/FeatureFlagService.java` (cache en mémoire, refresh toutes les 5 min)
- `config/controller/web/AdminConfigWebController.java`
- Templates : `admin/config/*.html`

**Annotation custom pour feature flags :**
```java
@RequiresModule("pharmacy")
@GetMapping("/pharmacy")
public String pharmacyDashboard() { ... }
// → AOP intercepte et renvoie 403 si module désactivé
```
