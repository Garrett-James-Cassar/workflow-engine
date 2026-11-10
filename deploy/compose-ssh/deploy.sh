#!/bin/sh
# deploy/compose-ssh/deploy.sh <env> <tag>: DOCKER_HOST=ssh://$DEPLOY_HOST docker compose up, then poll /ready for 60 s.
# DEPLOY_HOST (user@host) is required. The image comes from $REGISTRY, or is streamed over ssh when REGISTRY is empty.
# The host keeps its secrets in .env.<env> next to where compose runs (ENV_FILE); the repo never holds them.
# On failure it redeploys the previous tag and exits 1; ROLLBACK=1 skips that.
set -eu
ENV_NAME="${1:?usage: deploy.sh <env> <tag>}"
TAG="${2:?usage: deploy.sh <env> <tag>}"
: "${DEPLOY_HOST:?DEPLOY_HOST (user@host) is required for the compose-ssh target}"
HERE="$(cd "$(dirname "$0")" && pwd)"
export DOCKER_HOST="ssh://$DEPLOY_HOST" TAG REGISTRY="${REGISTRY:-}" ENV_FILE="${ENV_FILE:-.env.$ENV_NAME}"
PORT="${DEPLOY_PORT:-47831}"
HOST="${DEPLOY_HOST#*@}"
PROJECT="workflow-engine-$ENV_NAME"

previous=$(docker ps --filter "label=com.docker.compose.project=$PROJECT" --format '{{.Label "dev.workflow.tag"}}' 2>/dev/null | head -1 || true)
if [ -z "$REGISTRY" ]; then
  (unset DOCKER_HOST; docker save "workflow-engine-api:$TAG") | docker load >/dev/null
fi
docker compose -p "$PROJECT" -f "$HERE/compose.yml" up -d

i=0
while [ "$i" -lt 30 ]; do
  if curl -fsS "http://$HOST:$PORT/ready" >/dev/null 2>&1; then echo "deploy: /ready is 200 for $TAG on $HOST"; exit 0; fi
  i=$((i + 1)); sleep 2
done
echo "deploy: /ready did not answer within 60 s for $TAG" >&2
if [ "${ROLLBACK:-}" != "1" ] && [ -n "$previous" ] && [ "$previous" != "$TAG" ]; then
  echo "deploy: rolling back to $previous" >&2
  ROLLBACK=1 "$0" "$ENV_NAME" "$previous" || true
fi
exit 1
