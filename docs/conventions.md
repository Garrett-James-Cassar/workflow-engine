# Conventions (moved out of CLAUDE.md, 2026-11-08)

Details behind the one-line links in CLAUDE.md. The fleet standards (`/Users/garrett.cassar/Projects/fleet/standards/`) stay the source for the rules; this file holds how workflow-engine applies them. Routes live in OpenAPI (`/openapi.json`, `/docs`), not here.

## Input limits (fleet SECURITY.md)
- Every route has a request schema: Bean Validation on bodies (`@Valid`), path and query (`@Name`, `@Min`/`@Max`, `@Pattern`); no hand-written input checks in handlers. Jackson rejects unknown fields (`fail-on-unknown-properties`). Rejections are 400 problem+json with `errors[]` of paths (`config/web/ValidationProblems.kt`), never values; `BodyGuardFilter` answers 413 (1 MB) and 415. `InputValidationTest` fails if a route has no schema.
- Rate limits (Bucket4j, `config/web/RateLimitInterceptor.kt`), per user else IP: read 300, write 60, heavy 20 per minute (`RATE_LIMIT_*_PER_MIN`). Mark an expensive route `@Requires(..., heavy = true)`. `RATE_LIMIT_ENABLED=false` only in tests (`ApiTest`).
- Responses are validated against the DTO constraints in dev auth mode (500 on a mismatch), logged at error in live.

## API versioning
- Every business route is served under `/api/v1` (`api/ApiVersion.kt` mounts it once, for the controllers in `dev.workflow.app.api`). `/health`, `/docs`, `/openapi.json` and the `/demo/*` helpers stay at the root (`/` redirects to `/docs`); OpenAPI lists `servers: [{url: /api/v1}]`.
- The old unversioned paths still answer as deprecated aliases (`Deprecation`, `Sunset` 90 days out, `Link: …; rel="successor-version"`), until the fleet callers have moved; then delete `DeprecatedAliasFilter`.
- The studio's one base setting is `VITE_API_BASE_URL` plus `/api/v1`.

## Observability
- Logs are ECS JSON on stdout (`logging.structured` in `application.yaml`, `logback-spring.xml`); no `println` in production code (`NoStrayOutputTest`). Tracing is Boot's OpenTelemetry support: OTLP export (traces, metrics, logs) is off unless `OTEL_EXPORTER_OTLP_ENDPOINT` is set.
- Every route declares a human label next to its permission: `@Requires(Permissions.X, label = "Start run")` (verb-first, sentence case, UI-SPEC words); `@Public` routes use their route. Every `@Scheduled` job carries `@Job(label, action)`. `RouteInventoryTest` fails otherwise. The label, `event.action` and `labels.origin` are on every log line and span (`observability/Endpoints.kt`).

## The Demo (studio)
The React studio in `apps/web` is the app's only UI. Its Demo is the mocked build (`make web-demo`, `make web-demo-build`, `make share` through ngrok): the Demo plays real Cypress specs in the viewer's own tab, with no engine needed.
- The chapters are specs in `apps/web/cypress/e2e/{smoke,features}`, listed in `apps/web/src/demo/chapters.ts` (a title must match its `it(...)`; the chapters menu is in play order). The same files run under real Cypress and in-page (`src/demo/vendor/runtime.ts`, vendored). Stay inside the command subset that runtime supports.
- Hooks are `data-cy` attributes; never select by text or class.
- Gate: `make web-demo-check` plays every chapter unpaced (demo.7); `cypress/e2e/demo/chapters.cy.ts` also asserts that no `/api/` request escapes the mocks.
- `POST /demo/reset` (engine, dev auth only) is for `make reseed` and the audit tests, not for the studio.
- The engine's HTML diagram export (`format=html`) still serves the vendored `static/vendor/mermaid.min.js`; that file is the only static resource left.

## UI standards (workflow studio, `apps/web`)

`apps/web` is the new React studio (pnpm package beside the Gradle build). It is written against its own contract, `apps/web/contract/openapi.yaml` (the API it wants, under `/api/v1`), never against the engine's current endpoints. Components see only the `ApiPort` interface in `apps/web/src/api`. Pointing it at a real backend means writing one new adapter. UI work in `apps/web` does not change `engine/*`.

Binding docs: `/Users/garrett.cassar/Projects/fleet/standards/ui/STYLE.md` v1.4 (with `specimen.html`), `INDEPENDENCE.md`, `ports.json`, [`UI-SPEC.md`](UI-SPEC.md) (keep it current: ui.2) and [`docs/ui-plan.md`](docs/ui-plan.md).

Decisions (don't relitigate; raise concerns in `docs/ui-plan.md` §8):
- React 18 + Vite + TypeScript + Tailwind with STYLE tokens exactly. Web dev port **5183** (`strictPort`); the fleet serves the demo build on **15183**. The demo build is `pnpm -C apps/web build:demo` (`vite build --mode demo`) and needs no backend.
- Contract first: edit `contract/openapi.yaml`, then `pnpm -C apps/web gen:api`. The mock must pass `src/mocks/contract.test.ts` (part of `make ci`).
- `VITE_MODE` = `mock` (MSW + seeded Object Mother, paging/sort/filter, 150–400 ms, `?scenario=empty|error|slow`) | `demo` (the mock reset per load, mode banner, Demo button with the 7 chapters) | `live` (`VITE_API_BASE_URL`). The mock routes by following the exported graph's outcome edges through a small table, never a second copy of engine logic.
- Every data table is a local `FleetGrid` (AG Grid Community, `fleetGridTheme`, `minWidth` on every column). Anything else is grid-exempt in UI-SPEC.md. Statuses are our own words on the five STYLE colours (UI-SPEC "Statuses").
- Every operation goes through `can(permission)` (true until central RBAC lands: ui.3/4/6 are tbd). The "Acting as" persona sends `X-User`. Histories (runs, incidents) are export-only.
- Cypress only: `cypress/e2e/{smoke,features,demo}`, `data-cy`, `cy.a11y()` (cypress-axe) in smoke, coverage ≥ 80%, smoke on lefthook pre-push, `cy:live` nightly. The 7 Demo chapters are real specs played in-page. The engine's Preview mode (E8) is not part of this.
- `make ci` = Gradle `check` plus the web gate (`make web-ci`). `make share` serves the demo build via ngrok (`NGROK_AUTHTOKEN` from env).

### Fleet deviations (apps/web)
- `src/demo/vendor` is vendored from rules-engine's walkthrough runtime and excluded from lint and complexity gates.
