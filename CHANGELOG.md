# Changelog

All notable changes to this project are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and commits follow [Conventional Commits](https://www.conventionalcommits.org/).

## [Unreleased]

### Added
- Portable resources: export and import routes per Config kind, the whole-configuration bundle (`GET /api/v1/export`, `POST /api/v1/import/preview`, `POST /api/v1/import`), a 5 MB import cap (`MAX_IMPORT_BYTES`), and `docs/RESOURCES.md` classifying every resource.
- Studio: Export all and a row menu on the workflows, runs, incidents and waits grids, Import on workflows (file, preview by kind, confirm), a Configuration screen for the bundle, and permissions read from `GET /api/v1/me`.
- `GET /ready` checks the engine's own database (503 naming the failing check).
- Security headers and strict CORS from `CORS_ORIGINS`; strict request schemas, 413/415, problem+json errors and in-process rate limits.
- Observability: ECS JSON logs, OpenTelemetry traces and RED metrics, optional OTLP export.
- Business routes under `/api/v1`, with the old unversioned paths kept as deprecated aliases; `openapi.json` committed and checked for staleness, `make api-breaking`.
- Role packages with ArchUnit and detekt limits enforced in `check`.
- Gate tiers `make fast`, `make push-gate` and `make ci`; lefthook hooks, including a Conventional Commits check on commit messages.
- `RUNBOOK.md` (deploy, rollback, incidents) and an environment and ports table in the README.

- Data management: `make backup`, `make restore` and `make restore-drill` (a tested restore), `make seed` and `make reseed` (Object Mother data through the API), `MigrationTest`, and the `ALLOW_RESET` guard (default false; startup fails with it true in production). RPO 24 h and RTO 1 h in NFR.md.

### Changed
- The studio's contract and mocks use the engine's real export envelope (`{kind, version, key, ...}`, exports are arrays).
