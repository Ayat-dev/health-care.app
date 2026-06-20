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

### Domaine public

Préférer **Let's Encrypt** (certbot / Caddy) et monter les certificats émis à la
place des fichiers auto-signés.

## 2. Démarrer

```bash
docker compose up -d
```

- `http://<hôte>`  → redirigé en `https://<hôte>` (301)
- `https://<hôte>` → application (TLS terminé par nginx, relais vers `backend:8080`)

HSTS est activé (1 an). Spring est configuré (`server.forward-headers-strategy=framework`,
cookie de session `Secure`) pour fonctionner correctement derrière le proxy.
