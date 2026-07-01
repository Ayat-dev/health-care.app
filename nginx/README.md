# Reverse-proxy TLS — ClinicApp (P2.5, chiffrement en transit)

Le service `nginx` du `docker-compose.yml` termine le HTTPS et relaie vers le
backend. Le backend n'est **pas** exposé sur l'hôte : tout le trafic externe
passe par TLS.

## 1. Générer le certificat (obligatoire avant le 1er `docker compose up`)

nginx refuse de démarrer sans `nginx/certs/clinic.crt` + `nginx/certs/clinic.key`.
Ces fichiers sont **gitignorés** (la clé privée ne doit jamais être versionnée).

### Certificat interne auto-signé (LAN / clinique sans domaine public)

```bash
mkdir -p nginx/certs
openssl req -x509 -nodes -newkey rsa:2048 -days 825 \
  -keyout nginx/certs/clinic.key \
  -out    nginx/certs/clinic.crt \
  -subj   "/C=SN/O=ClinicApp/CN=clinic.local" \
  -addext "subjectAltName=DNS:clinic.local,IP:192.168.1.10"
```

> Adapter `CN`/`subjectAltName` au nom d'hôte ou à l'IP du serveur sur le LAN.
> Un certificat auto-signé déclenche un avertissement navigateur : importer
> `clinic.crt` comme autorité de confiance sur les postes clients (ou déployer
> une PKI interne) pour le supprimer.

### Domaine public → **Let's Encrypt automatisé (Z6)**

Si la clinique a un **domaine public** (DNS pointant sur l'hôte, ports 80/443
ouverts depuis Internet), utiliser le **certificat Let's Encrypt automatisé** au
lieu de l'auto-signé — émission + **renouvellement automatique**, sans openssl
manuel.

1. Renseigner dans `.env` : `DOMAIN`, `LETSENCRYPT_EMAIL` (et `LETSENCRYPT_STAGING=1`
   pour un essai à blanc, puis `0`).
2. Émission initiale (une fois) :
   ```bash
   ./init-letsencrypt.sh
   ```
   Le script pose un certificat factice le temps que nginx démarre, fait valider
   le domaine par Let's Encrypt (challenge http-01 via le webroot nginx), puis
   recharge nginx avec le vrai certificat (chemin fixe `live/clinic/`, via
   `--cert-name clinic` — donc indépendant du domaine).
3. Démarrer / mettre à jour toute la stack **avec le calque** :
   ```bash
   docker compose -f docker-compose.yml -f docker-compose.letsencrypt.yml up -d
   ```

Le service `certbot` renouvelle ensuite tout seul (vérification toutes les 12 h,
renouvellement effectif < 30 j avant expiration) et nginx recharge le certificat
en boucle (toutes les 6 h) — **aucune intervention manuelle**.

> Le certificat auto-signé du §1 reste le mode par défaut (LAN / sans domaine) :
> ne pas inclure `-f docker-compose.letsencrypt.yml` et nginx utilise `nginx.conf`.

## 2. Démarrer

```bash
docker compose up -d                      # mode auto-signé LAN (défaut)
# ou, en mode Let's Encrypt (après init-letsencrypt.sh) :
# docker compose -f docker-compose.yml -f docker-compose.letsencrypt.yml up -d
```

- `http://<hôte>`  → redirigé en `https://<hôte>` (301 ; le challenge ACME
  `/.well-known/acme-challenge/` n'est PAS redirigé en mode Let's Encrypt)
- `https://<hôte>` → application (TLS terminé par nginx, relais vers `backend:8080`)

HSTS est activé (1 an). Spring est configuré (`server.forward-headers-strategy=framework`,
cookie de session `Secure`) pour fonctionner correctement derrière le proxy.
