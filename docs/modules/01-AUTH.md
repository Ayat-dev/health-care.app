# Module 01 — Authentification & Gestion des rôles

## Objectif
Contrôler l'accès à toutes les fonctionnalités selon le rôle de l'utilisateur. Deux mécanismes coexistent : JWT pour le client desktop (stateless), session cookie pour l'interface web (stateful).

## Rôles & permissions

| Rôle | Patients | Consultations | Pharmacie | Facturation | Labo | Admin |
|---|---|---|---|---|---|---|
| ADMIN | ✅ tout | ✅ tout | ✅ tout | ✅ tout | ✅ tout | ✅ tout |
| MEDECIN | ✅ ses patients | ✅ ses consultations | 👁 lecture | 👁 lecture | ✅ prescrire | ❌ |
| INFIRMIER | 👁 lecture | ✅ constantes | ❌ | ❌ | 👁 lecture | ❌ |
| SECRETAIRE | ✅ créer/modifier | ❌ | ❌ | ✅ tout | ❌ | ❌ |
| PHARMACIEN | 👁 ordonnances | ❌ | ✅ tout | 👁 lecture | ❌ | ❌ |
| LABORANTIN | 👁 lecture | ❌ | ❌ | ❌ | ✅ résultats | ❌ |
| CAISSIER | 👁 lecture | ❌ | ❌ | ✅ encaisser | ❌ | ❌ |
| PATIENT | 👁 son dossier | 👁 ses consultations | ❌ | 👁 ses factures | 👁 ses résultats | ❌ |

## Endpoints REST

```
POST /api/auth/login          → { token, username, role, fullName }
POST /api/auth/refresh        → { token }          (renouveler le JWT)
POST /api/auth/logout         → 200 OK             (invalide la session web)
GET  /api/auth/me             → UserDto             (profil de l'utilisateur connecté)
POST /api/auth/change-password → 200 OK
```

## Endpoints Admin (ADMIN uniquement)

```
GET    /api/admin/users         → liste des utilisateurs
POST   /api/admin/users         → créer un utilisateur
PUT    /api/admin/users/{id}    → modifier
PATCH  /api/admin/users/{id}/toggle  → activer/désactiver
DELETE /api/admin/users/{id}    → suppression logique
```

## Routes web (Thymeleaf)

```
GET/POST /login                 → page de connexion
GET      /logout                → déconnexion
GET      /admin/users           → liste utilisateurs
GET/POST /admin/users/new       → créer un utilisateur
GET/POST /admin/users/{id}/edit → modifier un utilisateur
GET      /profile               → profil de l'utilisateur connecté
GET/POST /profile/password      → changer son mot de passe
```

## Règles métier
- Le mot de passe initial est généré et envoyé par SMS/email à la création du compte
- `first_login = true` → l'utilisateur doit changer son mot de passe dès la première connexion
- Mot de passe : minimum 8 caractères, au moins 1 chiffre
- Token JWT valide 24h ; un refresh token valide 7 jours (à implémenter Phase 2)
- Après 5 tentatives échouées → compte verrouillé 15 minutes
- Toute connexion est enregistrée dans `user_sessions`

## Implémentation Java

**Classes à créer/modifier :**
- `entity/User.java` ← déjà existant
- `security/JwtService.java` ← déjà existant
- `security/JwtAuthFilter.java` ← déjà existant
- `security/UserDetailsServiceImpl.java` ← déjà existant
- `config/SecurityConfig.java` ← déjà existant
- `controller/api/AuthApiController.java` ← déjà existant
- `controller/web/AdminUserWebController.java` ← à créer
- `service/UserService.java` ← à créer
- `dto/UserDto.java` ← à créer

**Templates Thymeleaf :**
- `templates/auth/login.html` ← déjà existant
- `templates/admin/users/list.html`
- `templates/admin/users/form.html`
- `templates/profile/index.html`
