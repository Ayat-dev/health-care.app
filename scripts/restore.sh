#!/usr/bin/env bash
#
# Restauration de la base PostgreSQL de ClinicApp depuis un dump.
# ⚠️  DESTRUCTIF : écrase les données actuelles par celles du dump.
# Accepte un fichier .sql ou .sql.gz (dumps du service db-backup OU de backup.sh).
#
# Usage :  scripts/restore.sh <fichier_dump>
#
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTAINER="${POSTGRES_CONTAINER:-medical_postgres}"

if [[ $# -lt 1 ]]; then
  echo "Usage : $0 <fichier_dump(.sql|.sql.gz)>" >&2
  exit 1
fi
FILE="$1"
[[ -f "$FILE" ]] || { echo "Fichier introuvable : $FILE" >&2; exit 1; }

if [[ -f "$ROOT_DIR/.env" ]]; then
  # shellcheck disable=SC1091
  set -a; source "$ROOT_DIR/.env"; set +a
fi
DB_USER="${POSTGRES_USER:-clinic_user}"
DB_NAME="${POSTGRES_DB:-clinicdb}"

echo "⚠️  Vous allez ÉCRASER la base '$DB_NAME' (conteneur $CONTAINER) avec :"
echo "    $FILE"
read -r -p "Taper 'RESTORE' pour confirmer : " ANSWER
[[ "$ANSWER" == "RESTORE" ]] || { echo "Annulé."; exit 1; }

echo "→ Restauration en cours…"
if [[ "$FILE" == *.gz ]]; then
  gunzip -c "$FILE" | docker exec -i "$CONTAINER" psql -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME"
else
  docker exec -i "$CONTAINER" psql -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME" < "$FILE"
fi

echo "✓ Restauration terminée. Redémarrez le backend : docker compose restart backend"
