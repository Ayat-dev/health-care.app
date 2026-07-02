#!/usr/bin/env bash
#
# Sauvegarde manuelle à la demande de la base PostgreSQL de ClinicApp.
# Le service `db-backup` du docker-compose fait déjà des sauvegardes planifiées ;
# ce script sert aux sauvegardes ponctuelles (avant une migration, un export, etc.)
# et comme brique pour une crontab hôte si l'on ne veut pas le conteneur dédié.
#
# Usage :  scripts/backup.sh [dossier_destination]
#          (destination par défaut : ./backups)
#
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="${1:-$ROOT_DIR/backups}"
CONTAINER="${POSTGRES_CONTAINER:-medical_postgres}"

# Charge POSTGRES_USER / POSTGRES_DB depuis .env si présent (sinon valeurs par défaut).
if [[ -f "$ROOT_DIR/.env" ]]; then
  # shellcheck disable=SC1091
  set -a; source "$ROOT_DIR/.env"; set +a
fi
DB_USER="${POSTGRES_USER:-clinic_user}"
DB_NAME="${POSTGRES_DB:-clinicdb}"

mkdir -p "$DEST"
DATE="$(date +%Y-%m-%d_%H%M%S)"
FILE="$DEST/clinicdb_${DATE}.sql.gz"

echo "→ Sauvegarde de '$DB_NAME' (conteneur $CONTAINER) vers $FILE"
docker exec -t "$CONTAINER" pg_dump -U "$DB_USER" --clean --if-exists "$DB_NAME" | gzip > "$FILE"

# Rétention : supprime les dumps manuels de plus de 30 jours.
find "$DEST" -maxdepth 1 -name 'clinicdb_*.sql.gz' -mtime +30 -delete 2>/dev/null || true

echo "✓ Sauvegarde terminée : $FILE ($(du -h "$FILE" | cut -f1))"
echo "  Pensez à copier ce fichier HORS-SITE (rsync, USB, stockage objet)."
