#!/bin/sh
# UniLodge container entrypoint.
#
# The container starts as root only so it can take ownership of mounted
# storage (Render mounts persistent disks as root). It then drops to the
# unprivileged "unilodge" user and starts the application.
set -eu

UPLOAD_DIR="${UPLOAD_DIR:-/app/uploads}"
LOG_FILE="${LOG_FILE:-/app/logs/unilodge.log}"
LOG_DIR="$(dirname "$LOG_FILE")"

mkdir -p "$UPLOAD_DIR" "$LOG_DIR"

if [ "$(id -u)" = "0" ]; then
    chown -R unilodge:unilodge "$UPLOAD_DIR" "$LOG_DIR" 2>/dev/null || true
    APP_UID="$(id -u unilodge)"
    APP_GID="$(id -g unilodge)"
    if command -v setpriv >/dev/null 2>&1; then
        exec setpriv --reuid="$APP_UID" --regid="$APP_GID" --init-groups \
            java -jar /app/unilodge.jar "$@"
    fi
    # Fallback for minimal base images without util-linux.
    exec chroot --userspec="$APP_UID:$APP_GID" / java -jar /app/unilodge.jar "$@"
fi

exec java -jar /app/unilodge.jar "$@"
