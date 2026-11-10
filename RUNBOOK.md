# Runbook

How to deploy, roll back and handle an incident for workflow-engine. Commands run from the repo root. There is no deployed environment yet (no deploy target, no registry); the procedure below is the local and Docker one, and the config bundle works the same against any running engine.

## Deploy

1. Gate the commit: `make ci` must be green (Gradle check, web gate, Cypress, Demo chapters).
2. Build the engine: `./gradlew :engine:app:bootJar` (the jar is `engine/app/build/libs/`). Run it with Java 25: `java -jar engine/app/build/libs/<jar>`.
3. Provide Postgres and set the env: `DATABASE_URL` (JDBC), `DB_USER`, `DB_PASSWORD`, plus `AUTH_MODE=live`, `RBAC_URL`, `TLS=true` and `CORS_ORIGINS` for a real environment (full table in [README](README.md)). Locally, `make up` starts the compose Postgres on 127.0.0.1:55434 and `make dev` runs the engine.
4. Start it. Flyway applies pending migrations (`engine/app/src/main/resources/db/migration`) before the app serves traffic.
5. Verify: `curl localhost:47831/health` answers `{"status":"ok"}` and `curl localhost:47831/ready` answers 200 (it checks the database).
6. Promote configuration between environments (workflows are Config; see [docs/RESOURCES.md](docs/RESOURCES.md)):
   1. Source: `curl -H 'X-User: e.admin' localhost:47831/api/v1/export > export.json` (or **Configuration > Export configuration** in the studio).
   2. Target: `curl -XPOST -H 'X-User: e.admin' -H 'Content-Type: application/json' --data @export.json <target>/api/v1/import/preview`. Read the plan: create, overwrite, unchanged, conflict, missing-ref. A missing-ref (a handler the target does not have) means deploy that build first.
   3. Target: the same call to `<target>/api/v1/import` applies it, all or nothing.
   `${VAR}` placeholders in the bundle are resolved from the target's environment; never put a secret in the file.

## Rollback

1. Stop the new engine (`make stop` locally, or stop the process or container).
2. Start the previous build: check out the previous commit (`git log --oneline`, `git checkout <sha>`) and rebuild, or run the previous jar or image.
3. Migrations are forward-only. Do not edit or delete an applied `V<n>__*.sql`. If the new migration is compatible with the old code (additive columns and tables, which is the rule), the old build runs against the migrated database. If not, fix forward: add `V<n+1>__*.sql` that repairs it and ship that.
4. Config rolls back with the bundle: import the `export.json` saved before the change (preview first).
5. Only for a throwaway local database: `make down` deletes all data, and the next `make dev` starts empty.

## Incidents

1. Is it up? `curl localhost:47831/health` (liveness) and `/ready` (503 names the failing check, usually the database). A failing `/ready` with a healthy `/health` means check Postgres first (`docker compose ps`, port 55434).
2. Logs: the engine writes ECS JSON to stdout (a terminal for `make dev`, the container logs otherwise). With `OTEL_EXPORTER_OTLP_ENDPOINT` set they also go to the collector. Raise detail with `LOG_LEVEL=DEBUG` and restart.
3. Correlate: every request has a trace id. The response carries a `traceparent` header (the 32 hex characters in the middle are the trace id); search the logs for that `trace.id`. Error responses are problem+json, and the studio shows the trace id in its error state.
4. A workflow that is stuck: an instance in **incident** failed a step; fix the cause, then `POST /api/v1/instances/{id}/retry`. To move one back, `POST /api/v1/instances/{id}/restart` with `{"step": "<user task>"}` (needs `workflow-support`, or `e.admin`). Waits with a timeout fire on their own every `TIMEOUT_POLL_INTERVAL`.
5. Restart: `make dev` (stops the old engine, starts Postgres, runs the engine). `make stop` only stops it. State lives in Postgres, so a restart loses nothing.
6. Known failure modes:
   - Port 47831 held by another program: `make stop` refuses to touch it and names the pid; use `PORT=<port> make dev` (the two samples that call the demo providers hard-code 47831).
   - 429 responses: the rate limit; wait, or raise `RATE_LIMIT_*_PER_MIN`.
   - 413 / 415: body over `MAX_BODY_BYTES` (`MAX_IMPORT_BYTES` for imports), or not JSON.
   - 400 on a valid-looking body: unknown fields are rejected; the `errors[]` paths say where.
   - A workflow `http { }` step refused: the host is not in `WORKFLOW_HTTP_ALLOWED_HOSTS`.
   - The studio shows CORS errors against the engine: add its origin to `CORS_ORIGINS`.
   - Tests need Docker (Testcontainers); `make fast` does not.
