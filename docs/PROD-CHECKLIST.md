# Checklist de mise en production — ClinicApp

> Runbook reproductible pour déployer une clinique **de zéro** sur un serveur, et
> pour vérifier qu'on est vraiment prêt. Cocher dans l'ordre. Références :
> [`DEPLOYMENT.md`](DEPLOYMENT.md) (topologie/TLS/backups) et `.env.example` (secrets).

## 0. Pré-requis serveur
- [ ] Machine Linux (ou Windows/WSL2) avec **Docker + Docker Compose** installés.
- [ ] Horloge synchronisée (NTP) — jetons JWT, TOTP MFA et audit en dépendent.
- [ ] Nom d'hôte / IP fixe sur le LAN ; **domaine public** si TLS Let's Encrypt visé.
- [ ] Disque avec marge pour la base **et** les sauvegardes (`./backups`) + `./uploads`.

## 1. Secrets (`.env`)
- [ ] `cp .env.example .env`
- [ ] Générer et renseigner les secrets **obligatoires** (fail-fast si absents) :
  - [ ] `POSTGRES_PASSWORD` (mot de passe fort)
  - [ ] `JWT_SECRET` (≥ 32 caractères aléatoires — `openssl rand -base64 48`)
  - [ ] `APP_ENCRYPTION_KEY` (clé de chiffrement PHI — voir `docs/SECURITY-ENCRYPTION.md`)
  - [ ] `MOBILE_MONEY_WEBHOOK_SECRET`, `MONITORING_PASSWORD`
  - [ ] `CLINIC_ADMIN_USERNAME` / `CLINIC_ADMIN_PASSWORD` (si amorçage headless ; sinon `/setup`)
- [ ] `JWT_REFRESH_COOKIE_SECURE=true` (déjà par défaut) — cohérent avec HTTPS.
- [ ] **Ne jamais** committer `.env` (déjà gitignoré).

## 2. TLS (chiffrement en transit)
- [ ] **LAN / sans domaine** : générer le certificat auto-signé (`nginx/README.md §1`).
- [ ] **Domaine public** : `DOMAIN` + `LETSENCRYPT_EMAIL` dans `.env`, puis `./init-letsencrypt.sh`.
- [ ] Vérifier que `nginx/certs/` contient bien le certificat **avant** le 1er démarrage.

## 3. Premier démarrage
- [ ] `docker compose up -d` (postgres + backend + **db-backup** + nginx)
- [ ] `docker compose ps` → tous `healthy` / `running`.
- [ ] `docker compose logs backend` → démarrage sans erreur, **Flyway** migre jusqu'à la dernière version, Hibernate `validate` OK.
- [ ] `curl -k https://<hôte>/actuator/health` → `{"status":"UP"}`.

## 4. Installation initiale (`/setup`)
- [ ] Ouvrir `https://<hôte>/` → l'assistant `/setup` s'affiche (si base vide et pas d'amorçage headless).
- [ ] Créer l'**admin**, la **clinique** et la config initiale.
- [ ] Se connecter ; vérifier que `/setup` n'est plus accessible (installation verrouillée).

## 5. Smoke fonctionnel (compte de test par rôle)
- [ ] Connexion admin → `/admin/users` : créer un utilisateur de chaque rôle utile.
- [ ] MEDECIN : créer un patient, une consultation, une ordonnance.
- [ ] CAISSIER : créer une facture, encaisser un paiement, imprimer un reçu (PDF).
- [ ] Vérifier le **journal d'audit** (`/admin/audit`) : les actions ci-dessus y figurent.
- [ ] Vérifier un **export Excel** (ex. `/billing/invoices/export`).

## 6. Sauvegardes (CRITIQUE — données médicales)
- [ ] `docker compose ps db-backup` → `running`.
- [ ] Forcer une sauvegarde : `scripts/backup.sh` → fichier dans `./backups/`.
- [ ] **Tester une restauration sur un environnement de staging** :
      `scripts/restore.sh backups/<dump>.sql.gz` puis re-smoke. *(Une sauvegarde jamais restaurée n'est pas une sauvegarde.)*
- [ ] Mettre en place la **copie hors-site** des `./backups` (rsync/USB/stockage objet).
- [ ] Prévoir la sauvegarde de `./uploads` (photos/documents patients) — séparée.

## 7. Notifications sortantes (optionnel mais recommandé)
- [ ] E-mail : renseigner `SPRING_MAIL_HOST` (+ user/pass/from) → redémarrer backend.
- [ ] SMS : renseigner `APP_SMS_AFRICASTALKING_API_KEY` (+ username/sender) → redémarrer.
- [ ] Test : `POST /api/notifications/test-sms` (ADMIN) ou déclencher un rappel de RDV, vérifier la réception.
- [ ] *(Sans configuration : les notifications sont simulées/loggées, elles ne partent pas.)*

## 8. Observabilité (optionnel)
- [ ] `docker compose --profile monitoring up -d` (Prometheus + Grafana).
- [ ] Sonde uptime externe (UptimeRobot) sur `https://<hôte>/actuator/health`.
- [ ] Vérifier la politique de rétention/rotation des logs Docker.

## 9. Durcissement final
- [ ] Confirmer que la **console H2 est inaccessible** (profil prod) et `show-sql` off.
- [ ] Confirmer le **lockout anti-brute-force** (5 échecs → compte verrouillé 15 min).
- [ ] Pare-feu : n'exposer que `443` (et `80` pour la redirection/ACME) ; `5432` fermé à l'extérieur.
- [ ] Changer/retirer les comptes de démonstration éventuels.

## 10. Rollback
- [ ] `docker compose down` puis redéploiement de l'image précédente si besoin.
- [ ] Restauration base depuis la dernière sauvegarde saine (`scripts/restore.sh`).
- [ ] Les migrations Flyway ne se **défont pas** automatiquement : tester tout upgrade sur staging d'abord.

---
> **CI** : chaque push/PR déclenche `.github/workflows/ci.yml` (build + `mvn verify`,
> Testcontainers PostgreSQL inclus). Ne déployer qu'une réf **verte**.
