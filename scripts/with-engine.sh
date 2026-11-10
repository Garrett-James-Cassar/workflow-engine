#!/bin/sh
# with-engine.sh <make args...>: start Postgres and the engine (Flyway migrates), wait for /ready, run `make <args>`,
# stop the engine, and exit with that make's status. Used by `make console-gate` and `make live-gate` (CI and laptops).
set -u
cd "$(dirname "$0")/.."
PORT="${PORT:-47831}"
make up >/dev/null || exit 1
./gradlew --no-daemon :engine:app:bootRun > engine.log 2>&1 &
engine=$!
trap 'make stop >/dev/null 2>&1; kill "$engine" 2>/dev/null' EXIT
i=0
until curl -fsS "http://127.0.0.1:$PORT/ready" >/dev/null 2>&1; do
  i=$((i + 1))
  if [ "$i" -gt 90 ]; then echo "with-engine: the engine did not come up in 180 s (see engine.log)" >&2; exit 1; fi
  sleep 2
done
make "$@"
