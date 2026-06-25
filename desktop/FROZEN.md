# 🧊 Client desktop — GELÉ (parking)

> **Décision (2026-06-25)** : on abandonne le développement actif du client lourd
> et on concentre toute l'activité sur **l'app web**. Tout le monde (secrétaires,
> médecins, infirmiers, caissiers, pharmaciens…) travaille désormais dans le
> navigateur. Ce dossier `desktop/` **n'est pas supprimé** : il reste comme socle
> à reprendre plus tard.

## Pourquoi ce gel

- Une seule interface à faire évoluer = vélocité maximale sur le métier.
- L'architecture est **déjà client/serveur** : le serveur (Spring Boot) expose
  l'API REST `/api/**` (stateless, JWT) ; l'app web n'en est qu'un client parmi
  d'autres. Le futur client desktop sera **un autre client de cette même API**.

## Vision de reprise — bundle « serveur + client »

Quand le desktop reprendra, il se déploiera comme **deux services distincts** :

| Service | Où | Rôle |
|---|---|---|
| **Serveur** | Un PC-serveur de la clinique (LAN) | Spring Boot + PostgreSQL. Sert l'app web ET l'API. C'est la moitié déjà existante. |
| **Client** | Le PC de chaque utilisateur | App JavaFX installée, parle à l'API du serveur sur le réseau local. |

**L'admin configure tout à la première installation** via l'assistant de premier
démarrage (`/setup`, voir `backend/.../setup/`). Côté client, la seule
configuration nécessaire sera **l'URL du serveur** (IP/hôte LAN) — l'auth se fait
ensuite par login → JWT.

## Le contrat de reprise

- **Point d'intégration unique = `/api/**`** (et `/fhir/**`). Tant que l'API reste
  complète et stable, le client desktop peut tout faire ce que fait le web.
  → *Règle* : toute capacité métier exposée au web doit aussi être joignable via l'API.
- Un client HTTP natif **n'est pas soumis au CORS** : la chaîne JWT suffit, pas de
  réglage CORS à prévoir pour le desktop.
- Temps réel : worklists via **STOMP sur `/ws`** (auth au CONNECT par JWT, déjà
  implémentée — voir `RealtimeClient`).

## Ce qui existe déjà ici (ne pas jeter)

- `util/ApiClient.java` — client REST + flux login/JWT.
- `util/RealtimeClient.java` — client STOMP/WebSocket (worklists temps réel).
- `controller/` + `fxml/` — login + 9 écrans (dashboard, patients, RDV,
  consultations, demandes d'examens, référentiels…).

## Ne PAS faire pour l'instant

- Ne pas l'ajouter au build CI ni à `docker-compose.yml` (déjà retiré).
- Ne pas le faire évoluer écran par écran : il rattrapera l'API d'un coup à la reprise.
