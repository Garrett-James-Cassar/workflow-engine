#!/bin/sh
# Nightly NFR tier (fleet standards/NFR.md section 8), run against the compose Postgres on 127.0.0.1:55434.
# Usage: nfr.sh seed | startup | load | memory | drill
#   seed     the section 4 volume (perf/seed-bulk.sql: 100 000 runs, 1 000 000 history rows); idempotent
#   startup  P4: start the engine jar, time it to /ready, stop it
#   load     P1-P3 with k6 (grafana/k6) against a fresh engine jar with the rate limiter off; leaves reports/nfr/rss.kb for P6
#   memory   P6: reports/nfr/memory.json from the RSS taken at the end of `load`
#   drill    A4/A5: dump, drop, restore, compare counts, write reports/nfr/restore.json
set -u
cd "$(dirname "$0")/.."
PORT="${PORT:-47831}"
FLEET="${FLEET:-$HOME/Projects/fleet}"
JAR=engine/app/build/libs/workflow-engine.jar
OUT=reports/nfr
mkdir -p "$OUT" .fleet-nfr
PSQL="docker compose exec -T db psql -q -U workflow -d workflow -v ON_ERROR_STOP=1"

build() { ./gradlew --quiet :engine:app:bootJar; }

start() {
  build || exit 1
  make stop >/dev/null 2>&1
  RATE_LIMIT_ENABLED=false ALLOW_RESET=false java -jar "$JAR" > "$OUT/engine.log" 2>&1 &
  echo $! > "$OUT/engine.pid"
  i=0
  until curl -fsS "http://127.0.0.1:$PORT/ready" >/dev/null 2>&1; do
    i=$((i + 1)); [ "$i" -gt 120 ] && { echo "nfr: the engine did not become ready (see $OUT/engine.log)" >&2; stop; exit 1; }
    sleep 1
  done
}

stop() { [ -f "$OUT/engine.pid" ] && kill "$(cat "$OUT/engine.pid")" 2>/dev/null; rm -f "$OUT/engine.pid"; sleep 1; }

seed() {
  # Flyway owns the schema: bring it up once on an empty database, then load the volume.
  if [ -z "$($PSQL -At -c "select to_regclass('public.instances')")" ]; then start; stop; fi
  $PSQL < perf/seed-bulk.sql
  $PSQL -At -F ' ' -c "select 'runs', count(*) from instances union all select 'history', count(*) from events"
}

startup() {
  build || exit 1
  make stop >/dev/null 2>&1
  LIMIT=20 OUT="$OUT/startup.json" RATE_LIMIT_ENABLED=false "$FLEET/standards/nfr/startup-timer.sh" "http://127.0.0.1:$PORT/ready" java -jar "$JAR"
}

load() {
  cp -r "$FLEET/standards/nfr/k6" .fleet-nfr/
  start
  docker run --rm --add-host=host.docker.internal:host-gateway -v "$PWD:/w" -w /w -e BASE_URL="http://host.docker.internal:$PORT" \
    -e SCENARIOS=perf/scenarios.json -e OUT="$OUT/k6.json" grafana/k6:0.54.0 run /w/.fleet-nfr/k6/fleet.js
  status=$?
  ps -o rss= -p "$(cat "$OUT/engine.pid")" > "$OUT/rss.kb"
  stop
  exit $status
}

memory() {
  python3 - <<PY
import json
kb = int(open("$OUT/rss.kb").read())
mb = round(kb / 1024)
json.dump({"P6": {"target": "< 768 MB (JVM)", "measured": mb, "unit": "MB RSS", "pass": mb < 768}}, open("$OUT/memory.json", "w"))
print(f"P6: engine RSS {mb} MB after the load run (limit 768)")
raise SystemExit(0 if mb < 768 else 1)
PY
}

drill() {
  log=$(scripts/db.sh drill) && ok=true || ok=false
  echo "$log"
  secs=$(echo "$log" | sed -n 's/.*took \([0-9]*\) s.*/\1/p')
  python3 - <<PY
import json
secs = float("${secs:-0}" or 0)
ok = "$ok" == "true"
json.dump({"A4": {"target": "RPO 24 h (nightly pg_dump)", "measured": "dump restores with identical counts" if ok else "counts differ", "pass": ok},
           "A5": {"target": "< 3600 s", "measured": secs, "pass": ok and secs < 3600}}, open("$OUT/restore.json", "w"))
PY
  [ "$ok" = true ]
}

"${1:-}"
