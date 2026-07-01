# Déploiement & topologie — ClinicApp

> Ce document décrit **comment ClinicApp est déployé sur le terrain** (un PC-serveur
> dans la clinique, des postes utilisateurs sur le réseau local), **comment l'admin
> configure tout à la première installation**, et **le contrat qui permettra de
> reprendre plus tard le client desktop** comme second client de la même API.
>
> Décision structurante (2026-06-25) : **web-first**. Toute l'activité passe par
> l'app web (navigateur). Le client desktop est gelé (`desktop/FROZEN.md`) mais
> l'architecture reste prête à l'accueillir sans refonte.

---

## 1. Topologie — déjà client/serveur

ClinicApp **est déjà** un système à deux niveaux. Le « serveur » et le « client »
ne sont pas un projet futur : ils existent, le client par défaut étant le navigateur.

```
                    Réseau local de la clinique (LAN)
   ┌─────────────────────────────────────────────────────────────┐
   │                                                             │
   │   PC-SERVEUR (une machine)                POSTES UTILISATEURS │
   │   ┌───────────────────────────┐          ┌──────────────┐   │
   │   │  nginx (TLS :443)         │  HTTPS   │  Navigateur  │   │
   │   │     │                     │◄─────────┤  (secrétaire,│   │
   │   │  Spring Boot :8080        │   LAN    │   médecin,   │   │
   │   │   ├─ Web Thymeleaf (UI)   │          │   caissier…) │   │
   │   │   ├─ API REST /api/** ────┼──────────┤              │   │
   │   │   └─ WebSocket /ws (STOMP)│          └──────────────┘   │
   │   │     │                     │          ┌──────────────┐   │
   │   │  PostgreSQL               │  (futur) │  Client      │   │
   │   │                           │◄─────────┤  desktop     │   │
   │   └───────────────────────────┘  API+JWT │  JavaFX      │   │
   │                                          └──────────────┘   │
   └─────────────────────────────────────────────────────────────┘
```

- **Le serveur** = Spring Boot + PostgreSQL (+ nginx pour le TLS). Il sert **à la fois**
  l'app web (sessions) et l'API REST (JWT stateless). C'est la moitié à installer sur
  le PC-serveur. Déploiement actuel : `docker-compose.yml` (voir §4).
- **Les clients** = aujourd'hui des navigateurs ; demain, en plus, le client desktop.

### Accessibilité réseau

Le serveur écoute sur **toutes les interfaces** (`0.0.0.0:8080` — aucun
`server.address` n'est fixé, c'est le défaut Spring Boot), donc joignable par les
autres postes du LAN via l'IP/le nom d'hôte du PC-serveur. En production, nginx
termine le TLS sur `:443` et relaie vers `backend:8080` (réseau Docker privé).

---

## 2. Première installation — l'admin configure tout (`/setup`)

Au **tout premier démarrage** sur une base vierge, **aucun utilisateur n'existe**.
C'est le signal « pas encore installé ». L'app bascule alors en mode installation :

1. N'importe quel accès web est **redirigé vers `/setup`** (`SetupGuardInterceptor`).
2. L'assistant (`templates/setup/wizard.html`) collecte en une page :
   - **le compte administrateur** (identifiant + mot de passe ≥ 8 caractères) ;
   - **l'identité de la clinique** (nom, adresse, téléphone, e-mail, devise, langue) ;
   - **les modules à activer** (pharmacie, labo, imagerie, hospitalisation, maternité, dentaire).
3. À la validation (`SetupService.complete`), on crée la clinique (tenant), le compte
   admin rattaché, on écrit l'identité + les modules dans `clinic_config`, puis on
   redirige vers `/login`.
4. Dès qu'un utilisateur existe, **l'assistant se verrouille** : `/setup` renvoie vers
   `/login`. Le verrou est à sens unique (pas de requête `COUNT` à chaque requête HTTP).

> **Aucun `.env` à éditer, aucune variable d'environnement applicative** : l'admin
> configure l'application depuis le navigateur. Les **secrets d'infrastructure**
> (mot de passe BDD, `JWT_SECRET`, clé de chiffrement PHI…) restent eux dans le
> `.env`/l'environnement — ils sont nécessaires *avant* le boot (voir §4).

### Chemin headless alternatif

Pour un déploiement scripté, `ProdDataInitializer` peut créer l'admin depuis
`CLINIC_ADMIN_USERNAME` / `CLINIC_ADMIN_PASSWORD`. S'il le fait, des utilisateurs
existent → l'assistant `/setup` se désactive automatiquement. Les deux chemins
coexistent sans conflit.

---

## 3. Le contrat « reprendre le desktop plus tard »

Le client desktop (gelé, cf. `desktop/FROZEN.md`) reprendra comme **second client de
la même API**, sans toucher au serveur. Les fondations sont déjà là :

| Besoin du futur client desktop | État aujourd'hui |
|---|---|
| Point d'intégration unique | **`/api/**`** (et `/fhir/**`) — chaîne de sécurité stateless JWT (`@Order(1)`). |
| Authentification | Login → access token (15 min) + refresh token rotatif (7 j), révocable. |
| Temps réel (worklists) | **STOMP sur `/ws`**, auth au CONNECT par JWT (client `RealtimeClient` déjà écrit). |
| Configuration côté client | **Une seule chose : l'URL du serveur** (IP/hôte LAN). |
| CORS | **Non concerné** : un client HTTP natif n'est pas soumis au CORS. |

> **Règle d'or à tenir pendant le développement web** : toute capacité métier exposée
> au web doit aussi être joignable via `/api/**`. Tant que l'API reste complète, le
> client desktop pourra tout faire — c'est *la* fondation à préserver.

---

## 4. Déploiement serveur (actuel — Docker)

```bash
cp .env.example .env        # renseigner les secrets (voir ci-dessous)
# Préparer le certificat TLS nginx AVANT le 1er démarrage (cf. nginx/README.md) :
docker compose up -d        # postgres + backend + nginx (mode auto-signé LAN par défaut)
# Option monitoring : docker compose --profile monitoring up -d
```

### TLS : auto-signé LAN (défaut) ou Let's Encrypt automatisé (Z6)

Deux modes, au choix selon que la clinique a un **domaine public** ou non :

- **LAN / sans domaine (défaut)** — certificat auto-signé généré à la main
  (`openssl`, cf. `nginx/README.md §1`). nginx utilise `nginx.conf`.
- **Domaine public → Let's Encrypt automatisé** — émission + **renouvellement
  automatiques**, aucune gestion manuelle de certificat :
  ```bash
  # 1. .env : DOMAIN + LETSENCRYPT_EMAIL (LETSENCRYPT_STAGING=1 pour un essai)
  ./init-letsencrypt.sh                                   # émission initiale (une fois)
  docker compose -f docker-compose.yml \
                 -f docker-compose.letsencrypt.yml up -d  # stack + renouvellement auto
  ```
  Le calque `docker-compose.letsencrypt.yml` bascule nginx sur `nginx.letsencrypt.conf`
  (challenge ACME http-01 + certificats certbot au chemin fixe `live/clinic/`) et ajoute
  un compagnon `certbot` qui renouvelle en boucle (12 h) ; nginx recharge toutes les 6 h.

Secrets obligatoires en prod (`application-prod.properties`, **fail-fast si absents**) :
`SPRING_DATASOURCE_URL/USERNAME/PASSWORD`, `JWT_SECRET`, `APP_ENCRYPTION_KEY`,
`MOBILE_MONEY_WEBHOOK_SECRET`, `MONITORING_PASSWORD`.

Au premier accès `https://<pc-serveur>/`, l'admin tombe sur l'assistant `/setup` (§2).

> **Packaging non-technique (installeur Windows natif) : non retenu pour l'instant.**
> Le déploiement serveur reste Docker. À ré-évaluer quand le client desktop reprendra.

---

## 5. Récapitulatif des composants (côté serveur)

| Composant | Rôle | Package / fichier |
|---|---|---|
| `SetupService` | État d'installation + création admin/clinique/config | `backend/.../setup/` |
| `SetupGuardInterceptor` | Redirige vers `/setup` tant que non installé | `backend/.../setup/` |
| `SetupWebController` | Sert et traite l'assistant `/setup` | `backend/.../controller/web/` |
| `SecurityConfig` | 3 chaînes : Actuator, API/JWT, Web/session (`/setup` en accès libre) | `backend/.../security/` |
| `ProdDataInitializer` | Amorçage admin headless (optionnel) | `backend/.../config/` |
| `docker-compose.yml` | Orchestration serveur (postgres + backend + nginx) | racine |
