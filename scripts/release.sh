#!/bin/sh
# make release: build the api and web images tagged with the short commit hash, push to $REGISTRY when it is set,
# then delete this app's local tags older than the newest 2 (CICD.md Disk hygiene). Never touches other apps' images.
set -eu
cd "$(dirname "$0")/.."
TAG="${TAG:-$(git rev-parse --short HEAD)}"
REPOS="workflow-engine-api workflow-engine-web"

docker build -t "workflow-engine-api:$TAG" -f Dockerfile .
docker build -t "workflow-engine-web:$TAG" apps/web

if [ -n "${REGISTRY:-}" ]; then
  for r in $REPOS; do
    docker tag "$r:$TAG" "$REGISTRY/$r:$TAG"
    docker push "$REGISTRY/$r:$TAG"
  done
else
  echo "release: REGISTRY is empty, images stay local"
fi

# docker images lists newest first: keep two tags per repository, remove the rest (never :local).
for r in $REPOS ${REGISTRY:+$(for x in $REPOS; do echo "$REGISTRY/$x"; done)}; do
  docker images "$r" --format '{{.Tag}}' | grep -v '^local$' | grep -v '^<none>$' | tail -n +3 | while read -r old; do
    echo "release: removing old image $r:$old"
    docker rmi "$r:$old" >/dev/null || true
  done
done
echo "release: $TAG"
