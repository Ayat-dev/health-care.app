#!/usr/bin/env bash
# ============================================================
#  Bootstrap Let's Encrypt (Z6) — première émission du certificat TLS.
#
#  À lancer UNE fois sur le serveur de prod, avec :
#   - un domaine public (DOMAIN dans .env) dont le DNS pointe sur cet hôte ;
#   - les ports 80 et 443 ouverts/joignables depuis Internet ;
#   - DOMAIN + LETSENCRYPT_EMAIL renseignés dans .env.
#
#  Ensuite, le service `certbot` du calque docker-compose.letsencrypt.yml
#  renouvelle automatiquement (et nginx recharge en boucle).
#
#  Astuce : mettre LETSENCRYPT_STAGING=1 dans .env pour tester contre
#  l'environnement de test de Let's Encrypt (quotas larges, certif non fiable),
#  puis repasser à 0 pour la vraie émission.
# ============================================================
set -euo pipefail

cd "$(dirname "$0")"
COMPOSE="docker compose -f docker-compose.yml -f docker-compose.letsencrypt.yml"

[ -f .env ] || { echo "✗ .env introuvable — copier .env.example puis le renseigner."; exit 1; }
set -a; . ./.env; set +a
: "${DOMAIN:?Définir DOMAIN dans .env (ex: clinique.example.com)}"
: "${LETSENCRYPT_EMAIL:?Définir LETSENCRYPT_EMAIL dans .env}"
STAGING="${LETSENCRYPT_STAGING:-0}"

CERT_NAME=clinic
LIVE="/etc/letsencrypt/live/${CERT_NAME}"

echo "### 1/4 — Certificat factice temporaire (pour que nginx puisse démarrer)…"
$COMPOSE run --rm --entrypoint "sh -c '\
  mkdir -p ${LIVE} && \
  openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
    -keyout ${LIVE}/privkey.pem \
    -out    ${LIVE}/fullchain.pem \
    -subj   \"/CN=${DOMAIN}\"'" certbot

echo "### 2/4 — Démarrage de nginx…"
$COMPOSE up -d nginx

echo "### 3/4 — Suppression du factice puis demande du vrai certificat…"
$COMPOSE run --rm --entrypoint "sh -c 'rm -rf \
  /etc/letsencrypt/live/${CERT_NAME} \
  /etc/letsencrypt/archive/${CERT_NAME} \
  /etc/letsencrypt/renewal/${CERT_NAME}.conf'" certbot

STAGING_ARG=""
[ "${STAGING}" != "0" ] && STAGING_ARG="--staging"

$COMPOSE run --rm --entrypoint certbot certbot certonly \
  --webroot -w /var/www/certbot \
  --cert-name "${CERT_NAME}" \
  -d "${DOMAIN}" \
  --email "${LETSENCRYPT_EMAIL}" \
  --agree-tos --no-eff-email --non-interactive \
  ${STAGING_ARG}

echo "### 4/4 — Rechargement de nginx avec le certificat émis…"
$COMPOSE exec nginx nginx -s reload

echo
echo "✓ TLS Let's Encrypt actif pour https://${DOMAIN}"
echo "  Démarrer / mettre à jour toute la stack :"
echo "    ${COMPOSE} up -d"
echo "  Le service certbot renouvelle automatiquement (vérif toutes les 12 h)."
