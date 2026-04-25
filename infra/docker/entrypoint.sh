#!/bin/bash
# `wait -n` is a bash builtin (not portable to BusyBox ash on older Alpine
# images), so the Dockerfile installs bash explicitly.

set -eu

if [ -z "${CLOUDFLARED_TOKEN:-}" ]; then
    echo "CLOUDFLARED_TOKEN is required (set via 'fly secrets set CLOUDFLARED_TOKEN=...')." >&2
    exit 1
fi

cloudflared tunnel --no-autoupdate run --token "$CLOUDFLARED_TOKEN" &
CLOUDFLARED_PID=$!

# shellcheck disable=SC2086
java $JAVA_OPTS -jar /app/app.jar &
APP_PID=$!

term() {
    kill -TERM "$CLOUDFLARED_PID" "$APP_PID" 2>/dev/null || true
}
trap term TERM INT

wait -n "$CLOUDFLARED_PID" "$APP_PID"
EXIT=$?
term
wait || true
exit "$EXIT"
