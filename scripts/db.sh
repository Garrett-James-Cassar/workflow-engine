#!/bin/sh
# dm.3: backup, restore and a restore drill for the compose Postgres (service `db`, 127.0.0.1:55434).
# pg_dump and psql run inside the container, so nothing needs installing. Usage: db.sh backup | restore FILE | drill
set -eu
cd "$(dirname "$0")/.."
DC="docker compose exec -T db"
PSQL="$DC psql -q -U workflow -d workflow -v ON_ERROR_STOP=1"
COUNT_SQL="select 'instances', count(*) from instances union all select 'user_tasks', count(*) from user_tasks union all select 'waits', count(*) from waits union all select 'events', count(*) from events union all select 'config_docs', count(*) from config_docs union all select 'audit_log', count(*) from audit_log order by 1"

backup() {
  mkdir -p backups
  file="backups/workflow-$(date -u +%Y%m%d-%H%M%SZ).sql"
  $DC pg_dump -U workflow -d workflow --clean --if-exists --no-owner > "$file"
  echo "$file"
}

restore() {
  [ -n "${1:-}" ] && [ -f "$1" ] || { echo "usage: make restore FILE=backups/workflow-<stamp>.sql (the file must exist)" >&2; exit 1; }
  $PSQL < "$1"
  echo "restored $1"
}

counts() { $PSQL -At -F ' ' -c "$COUNT_SQL"; }

drill() {
  before=$(counts)
  file=$(backup)
  start=$(date +%s)
  $PSQL -c 'drop schema public cascade; create schema public;'
  restore "$file" >/dev/null
  end=$(date +%s)
  after=$(counts)
  if [ "$before" = "$after" ]; then
    echo "drill ok: row counts match after restore (wipe and restore took $((end - start)) s)"
    echo "$after"
  else
    echo "drill FAILED: row counts differ" >&2
    echo "before: $before" >&2
    echo "after:  $after" >&2
    exit 1
  fi
}

case "${1:-}" in
  backup) backup ;;
  restore) restore "${2:-}" ;;
  drill) drill ;;
  *) echo "usage: db.sh backup | restore FILE | drill" >&2; exit 1 ;;
esac
