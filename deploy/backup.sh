#!/usr/bin/env bash
# Dump the UniLodge database from the production Compose stack.
#
#   sudo bash deploy/backup.sh
#
# Writes backups/unilodge-YYYY-MM-DD-HHMM.dump and keeps 14 days by default
# (override with BACKUP_RETENTION_DAYS).
set -euo pipefail

cd "$(dirname "$0")/.."

RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-14}"
STAMP="$(date +%Y-%m-%d-%H%M)"
mkdir -p backups

docker compose -f docker-compose.prod.yml exec -T db \
    pg_dump -U unilodge_app -d unilodge -Fc > "backups/unilodge-${STAMP}.dump"

find backups -type f -name 'unilodge-*.dump' -mtime +"${RETENTION_DAYS}" -delete

echo "Backup written to backups/unilodge-${STAMP}.dump"
