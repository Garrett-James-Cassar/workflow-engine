# workflow-engine non-functional requirements

> Fleet standard: standards/matrices/README.md (Non-functionals) and standards/ui/STYLE.md (UX). Criterion nf.0. Numbers marked "proposed" are guesses to confirm. A test marked "to add" does not exist yet (nf.1); no k6 scripts exist in this repo today.

## 1. Scope and assumptions
Internal workflow tool. Expected users: about 20 operators, reviewers and requesters, 5 at once at peak, plus a few systems sending events. Designed volume (proposed): 5 000 workflow runs, 20 000 tasks and waits, 200 000 events in Postgres. One engine instance, one Postgres. Service tasks run inside the instance row lock (v1), so a slow handler blocks only that run.

## 2. Performance
| id | requirement | target | test |
|---|---|---|---|
| NFR-P1 | API read p95 latency (lists, run, events) at expected load | < 200 ms (proposed) | k6 `perf/read.js` (to add) |
| NFR-P2 | API write p95 latency (start run, complete task) for runs without a slow service task | < 500 ms (proposed) | k6 `perf/write.js` (to add) |
| NFR-P3 | Sustained throughput | 20 req/s with error rate < 1% (proposed) | k6 `perf/mixed.js` (to add) |
| NFR-P4 | Startup to `/health` (no `/ready` yet) | < 20 s (proposed) | `make dev` + wait-for-healthy (to add) |
| NFR-P5 | Largest list (runs, seeded volume of section 5) | interactive < 2 s on a mid-range laptop | Cypress timing against the live engine (to add; mocked run exists in `apps/web/cypress/e2e/smoke`) |
| NFR-P6 | Timeout poller fires a due wait | within 10 s of expiry (poll interval 5 s) | `EngineE2ETest` "E6 a timeout is just the TIMED_OUT outcome" (fires on demand, interval not timed) |
| NFR-P7 | Service task retry and HTTP timeout | 2 attempts with backoff; 30 s per HTTP call | `EngineE2ETest` "E4 a failing handler raises an incident, and retry resumes" |

## 3. Availability, recovery and data
| id | requirement | target | test |
|---|---|---|---|
| NFR-A1 | `/health` (liveness) and `/ready` (dependency check) are separate | always; `/ready` is missing | `EngineE2ETest` E1 covers `/health`; `/ready` to add |
| NFR-A2 | RPO (max data loss) | 24 h (daily `make backup`) | RUNBOOK.md "Backup and restore"; the drill proves a dump restores |
| NFR-A3 | RTO (time to restore) | 1 h | `make restore-drill` on 2026-11-10: counts matched, wipe and restore under 1 s for the seeded set (6 runs, 20 events); re-measure on a production-sized copy |
| NFR-A4 | Migrations apply on deploy; rollback is expand-contract, forward-fix only | applied by Flyway on start; no down migrations | `MigrationTest` (empty DB, and the latest migration over a database with data); RUNBOOK.md Rollback |
| NFR-A5 | Destructive operations blocked in production | `POST /demo/reset` is 404 unless `ALLOW_RESET=true` (default false), outside dev and demo, and needs `demo.reset`; startup fails with `ALLOW_RESET=true` in production | `ResetGuardTest`, `DemoResetTest` |
| NFR-A6 | Event history is append-only | always | Postgres trigger in `V1__init.sql`; `PostgresOnlyTest` "E1 the event log is append-only" |
| NFR-A7 | A failing service task becomes an incident, not a lost run | always | `EngineE2ETest` E4 incident and retry |

## 4. UX and accessibility (from standards/ui/STYLE.md; kept because the app has a UI)
The UI is the engine console (`engine/app/src/main/resources/static`) and the studio (`apps/web`). The console has known deviations (CLAUDE.md "Fleet deviations (UI)"); targets apply fully to the studio.
| id | requirement | target | test |
|---|---|---|---|
| NFR-U1 | Accessibility conformance | WCAG 2.2 AA; zero serious/critical axe violations | cypress-axe in `apps/web/cypress/e2e/smoke` (exists for the studio; console to add) |
| NFR-U2 | Keyboard | every action reachable by keyboard, visible focus ring | Cypress keyboard spec (to add) |
| NFR-U3 | Contrast | text >= 4.5:1, large text and controls >= 3:1, via tokens only | `apps/web/scripts/check-style.mjs` plus axe |
| NFR-U4 | Tap targets | >= 44x44 px under `pointer: coarse` | `apps/web/cypress/e2e/smoke/mobile.cy.ts` (partial; full check to add) |
| NFR-U5 | Motion | respects `prefers-reduced-motion` | `apps/web/cypress/e2e/demo/demo-a11y.cy.ts` (Demo only; rest to add) |
| NFR-U6 | Status never by colour alone | every status has a word | studio component specs (icon check to add) |
| NFR-U7 | Every data view has loading, empty and error states | all views | `apps/web/cypress/e2e/features/errors.cy.ts` with `?scenario=empty|error|slow` |
| NFR-U8 | Responsive | 375 px to 1920 px, no page-level horizontal scroll | `mobile.cy.ts` (layout spec at three sizes to add) |
| NFR-U9 | Feedback latency | action acknowledged < 100 ms | Cypress timing (to add) |

## 5. Data volume and load
Seed volumes (proposed): 5 000 runs across the four sample workflows, 20 000 tasks and waits, 200 000 events. No bulk seeder exists; it is to add, built on the Object Mother (`engine/app/src/test/kotlin/dev/workflow/app/Mother.kt`). Section 2 tests run against this volume.

## 6. Security and privacy
- Deny by default: identity (401) then permission (403) on every route; tested in `AuthorizationTest`.
- Dev trusts `X-User`; live verifies a Supabase JWT and ignores `X-User`. Live refuses to start without `RBAC_URL` and the JWT secret.
- The built-in `http` task calls only allow-listed hosts (`EngineE2ETest` allowlist test).
- Secrets from environment only; `.gitignore` should add `.env*` (gap). gitleaks runs on staged changes in pre-commit; no dependency or SAST scan in `make ci` yet (gap).
- Rate limits and max payload size: none yet (proposed: 256 KB request body).

## 7. Observability
Today: default Spring text logs and `/health`. Target: structured JSON logs without secrets, W3C `traceparent`, RED metrics over OpenTelemetry. None implemented; tests to add. No alert thresholds defined.

## 8. Portability and compatibility
Workflow definitions export as JSON graph and Mermaid (`DiagramsUpToDateTest`). Runs, tasks and events have no export or import yet. Browsers: latest 2 evergreen. Fleet ports: engine 47831, Postgres 55434, rbac 47861 in live mode (standards/ports.json).

## 9. Not required (and why)
- Multi-region failover and horizontal scaling: one internal instance; the instance row lock assumes a single writer path per run.
- Sub-100 ms API latency for runs with slow third-party service tasks: latency is dominated by the external call.
- High-volume event throughput: event senders are a handful of systems, not a stream.
