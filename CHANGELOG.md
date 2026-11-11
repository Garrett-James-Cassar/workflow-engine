# Changelog

All notable changes to this project are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and commits follow [Conventional Commits](https://www.conventionalcommits.org/).

## [Unreleased]

### Added
- Studio: the acting person's rights come from `/me`; the sidebar, buttons and exports show only what they may do, a refused screen says why, and one Cypress spec per persona checks the who-sees-what table in UI-SPEC.md. The studio Demo has ten chapters, including Audit.
- Requester gains `tasks.read` and workflow support gains `workflows.read` in the persona file, so the studio can show a requester their task and offer support a restart step.
- Nightly NFR tier (nf.1, nf.2): `make seed-bulk` (100 000 runs, 1 000 000 history rows, idempotent), `perf/scenarios.json`, `make nfr-load`, `nfr-startup`, `nfr-memory`, `restore-drill` and `make nfr` (writes the dashboard's `workflow-engine.nfr.json`), and the `nfr-nightly` workflow. `GET /api/v1/tasks` takes an optional `limit` (default 200, max 500). Migration V4 indexes open tasks.
- NFR (nf.0): the app's own NFR.md is gone; `dev.json` `nfr` holds the extras and `perf/nfr-map.json` maps each row to its test. HTTP calls to rbac time out after 5 s (`OutboundTimeoutTest`), and `make web-build` fails when the initial route's JS passes 500 KB gzipped.
- Audit log (sec.6): every state-changing use case, import, export, demo reset and timeout tick writes one `audit_log` row (actor, via, action, resource, outcome, changed field names, trace id) in the same transaction as the change; refused requests write a `denied` row. `GET /api/v1/audit` (filters, paging, `audit.read`) and `GET /api/v1/audit/export` (`audit.export`); no route updates or deletes. `X-Fleet-Caller` goes out on every outbound call and comes in as `via`. Migration V3 moves the old `import_audit` rows into `audit_log`. Studio: Audit screen.
- Portable resources: export and import routes per Config kind, the whole-configuration bundle (`GET /api/v1/export`, `POST /api/v1/import/preview`, `POST /api/v1/import`), a 5 MB import cap (`MAX_IMPORT_BYTES`), and `docs/RESOURCES.md` classifying every resource.
- Studio: Export all and a row menu on the workflows, runs, incidents and waits grids, Import on workflows (file, preview by kind, confirm), a Configuration screen for the bundle, and permissions read from `GET /api/v1/me`.
- `GET /ready` checks the engine's own database (503 naming the failing check).
- Security headers and strict CORS from `CORS_ORIGINS`; strict request schemas, 413/415, problem+json errors and in-process rate limits.
- Observability: ECS JSON logs, OpenTelemetry traces and RED metrics, optional OTLP export.
- Business routes under `/api/v1`, with the old unversioned paths kept as deprecated aliases; `openapi.json` committed and checked for staleness, `make api-breaking`.
- Role packages with ArchUnit and detekt limits enforced in `check`.
- Gate tiers `make fast`, `make push-gate` and `make ci`; lefthook hooks, including a Conventional Commits check on commit messages.
- `RUNBOOK.md` (deploy, rollback, incidents) and an environment and ports table in the README.

- Data management: `make backup`, `make restore` and `make restore-drill` (a tested restore), `make seed` and `make reseed` (Object Mother data through the API), `MigrationTest`, and the `ALLOW_RESET` guard (default false; startup fails with it true in production). RPO 24 h and RTO 1 h (fleet NFR.md).

### Changed
- The no-build console is retired (`static/` pages, `engine/console-e2e`, `make console-build`, `GET /demo/status`): the studio is the only UI and `/` redirects to `/docs`. The Mermaid file under `static/vendor/` stays for the HTML diagram export.
- The studio's contract and mocks use the engine's real export envelope (`{kind, version, key, ...}`, exports are arrays).
