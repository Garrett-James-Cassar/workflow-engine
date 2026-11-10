#!/bin/sh
# deploy/local/deploy.sh <env> <tag>: run the engine image on this machine (Docker), then poll /ready for 60 s.
# On failure it redeploys the previous tag and exits 1. ROLLBACK=1 skips that (used by `make rollback`).
# The database is the compose Postgres on host port 55434 (ports.json workflow-engine-postgres): `make up` first.
set -eu
ENV_NAME="${1:?usage: deploy.sh <env> <tag>}"
TAG="${2:?usage: deploy.sh <env> <tag>}"
IMAGE="${REGISTRY:+$REGISTRY/}workflow-engine-api"
NAME="workflow-engine-$ENV_NAME"
PORT="${DEPLOY_PORT:-47831}"
DB_PORT="${DB_PORT:-55434}"

previous=""
if docker inspect "$NAME" >/dev/null 2>&1; then
  previous=$(docker inspect -f '{{index .Config.Labels "dev.workflow.tag"}}' "$NAME" 2>/dev/null || true)
  docker rm -f "$NAME" >/dev/null
fi

echo "deploy: $IMAGE:$TAG as $NAME on :$PORT (env $ENV_NAME)"
docker run -d --name "$NAME" --restart unless-stopped \
  --label "dev.workflow.tag=$TAG" \
  -p "127.0.0.1:$PORT:47831" \
  --add-host host.docker.internal:host-gateway \
  -e "DATABASE_URL=${DATABASE_URL:-jdbc:postgresql://host.docker.internal:$DB_PORT/workflow}" \
  -e "DB_USER=${DB_USER:-workflow}" -e "DB_PASSWORD=${DB_PASSWORD:-workflow}" \
  -e "APP_ENV=$ENV_NAME" -e "APP_VERSION=$TAG" \
  "$IMAGE:$TAG" >/dev/null

i=0
while [ "$i" -lt 30 ]; do
  if curl -fsS "http://127.0.0.1:$PORT/ready" >/dev/null 2>&1; then
    echo "deploy: /ready is 200 for $TAG"
    exit 0
  fi
  i=$((i + 1)); sleep 2
done

echo "deploy: /ready did not answer within 60 s for $TAG" >&2
docker logs --tail 20 "$NAME" >&2 || true
if [ "${ROLLBACK:-}" != "1" ] && [ -n "$previous" ] && [ "$previous" != "$TAG" ]; then
  echo "deploy: rolling back to $previous" >&2
  ROLLBACK=1 "$0" "$ENV_NAME" "$previous" || true
fi
exit 1
