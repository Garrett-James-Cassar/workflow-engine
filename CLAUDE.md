# Working in workflow-engine

- `engine/core` must stay pure Kotlin (no Spring, no I/O). `Engine` is a pure transition function; the app applies its effects.
- Every step yields an enum outcome. Routing is `on { when (it) { … } }`, and the compiler's exhaustiveness check is the feature: never add `else` branches to sample workflows.
- New workflow: put the DSL `object : Workflow(...)` in `engine/app/.../app/workflow/` and list it in `adapters/Workflows.kt`; linked handler code lives in `app/samples`, then run `./gradlew diagrams` (DiagramsUpToDateTest fails otherwise). A behaviour change needs a version bump.
- Tests: core uses kotlin.test; compile-fail cases live in `CompileFailTest` (in-process kotlinc). App e2e uses Testcontainers Postgres plus a `MutableClock`. Prefix test names with the milestone (`E5 …`).
- Gate: `make ci` (`./gradlew check`).

## Architecture (fleet ARCHITECTURE-RULES.md)
- Role map in `fleet-layers.json`, described in `ARCHITECTURE.md`. Controllers (`app/api`) call services (`app/runtime`, `app/security`) and mappers only; services see ports (`app/ports`) and `app/domain`; `persistence`, `clients` and `adapters` implement the ports; `config` is the composition root.
- Enforced in `./gradlew check`: `ArchitectureTest` (ArchUnit), `detektEdge` and `detektCore` (limits in `config/detekt/*.yml`; never raise one or suppress a finding), and `duplication` (jscpd under 3%). A new class must land in a role package.

## Input limits (fleet SECURITY.md)
- Every route has a request schema: Bean Validation on bodies (`@Valid`), path and query (`@Name`, `@Min`/`@Max`, `@Pattern`); no hand-written input checks in handlers. Jackson rejects unknown fields (`fail-on-unknown-properties`). Rejections are 400 problem+json with `errors[]` of paths (`config/web/ValidationProblems.kt`), never values; `BodyGuardFilter` answers 413 (1 MB) and 415. `InputValidationTest` fails if a route has no schema.
- Rate limits (Bucket4j, `config/web/RateLimitInterceptor.kt`), per user else IP: read 300, write 60, heavy 20 per minute (`RATE_LIMIT_*_PER_MIN`). Mark an expensive route `@Requires(..., heavy = true)`. `RATE_LIMIT_ENABLED=false` only in tests (`ApiTest`).
- Responses are validated against the DTO constraints in dev auth mode (500 on a mismatch), logged at error in live.

## API versioning
- Every business route is served under `/api/v1` (`api/ApiVersion.kt` mounts it once, for the controllers in `dev.workflow.app.api`). `/health`, `/docs`, `/openapi.json` and the `/demo/*` helpers stay at the root; OpenAPI lists `servers: [{url: /api/v1}]`.
- The old unversioned paths still answer as deprecated aliases (`Deprecation`, `Sunset` 90 days out, `Link: …; rel="successor-version"`), until the fleet callers have moved; then delete `DeprecatedAliasFilter`.
- The console's single base setting is `API` in `static/app.js`; the studio's is `VITE_API_BASE_URL` plus `/api/v1`.

## Observability
- Logs are ECS JSON on stdout (`logging.structured` in `application.yaml`, `logback-spring.xml`); no `println` in production code (`NoStrayOutputTest`). Tracing is Boot's OpenTelemetry support: OTLP export (traces, metrics, logs) is off unless `OTEL_EXPORTER_OTLP_ENDPOINT` is set.
- Every route declares a human label next to its permission: `@Requires(Permissions.X, label = "Start run")` (verb-first, sentence case, UI-SPEC words); `@Public` routes use their route. Every `@Scheduled` job carries `@Job(label, action)`. `RouteInventoryTest` fails otherwise. The label, `event.action` and `labels.origin` are on every log line and span (`observability/Endpoints.kt`).

## Trunk-based development
- Work directly on `main`. No feature branches, no worktrees, no PRs.
- Make small commits. Run the fast gate (`./gradlew :engine:app:test :engine:core:test && pnpm -C apps/web lint && pnpm -C apps/web test`) before each commit.
- The full gate (`make ci`) runs once at the end of each task.
- Keep `main` green at all times. If it's red, fixing it comes before anything else.
- Unfinished features go behind a flag or a demo/mock mode, not on a branch.
- Push only via the coordinator's `/fleet-push`, never force-push.

## Fleet deviations (UI)
The console in `engine/app/src/main/resources/static` is a no-build page and **the Demo surface**: it runs against the real engine (dev auth), and its Demo plays real Cypress specs in the viewer's own tab. The React studio in `apps/web` is the mocked test UI against its own contract.

Console Demo (`engine/console-e2e`, a pnpm package beside `apps/web`):
- The 8 chapters are the specs in `engine/console-e2e/cypress/e2e/chapters`, listed in `src/chapters.ts` (a title must match its `it(...)`). The same files run under real Cypress and in-page (`src/vendor/runtime.ts`, vendored from the studio). Stay inside the command subset that runtime supports.
- Hooks are `data-cy` attributes in `app.js`. Add one there, then use it from a spec; never select by text or class.
- The player is built to `static/console-demo/` and **committed**. After changing `src/`, run `make console-build`; `make console-bundle-check` (in `console-ci`) fails if it is stale.
- The console loads the player only when `GET /demo/status` answers 200 (dev auth, `DEMO_ENABLED` not false); live gets 404. `POST /demo/reset` seeds each chapter.
- Actions go as the person chosen in the sidebar (`X-User`); pages are read as the auditor `r.viewer`, and the directory as `e.admin`. Only in dev auth.
- Gate: `make dev`, then `make console-ci` (typecheck, bundle freshness, chapters + smoke + layout, Demo chapters unpaced). CI runs it in the `console` job. `make demo` opens `/?demo`; `make share` tunnels the engine through ngrok (dev auth trusts `X-User`: share only for a demo).
- Layout is STYLE v1.5: `.page` 12-column grid, `.main-col`/`.side-col` for the Detail archetype, `.page.fill` lists fill the height. `cypress/e2e/layout` proves it at 1920×1080, 1440×900 and 390×844.

Console deviations from STYLE:
- **Fonts:** it uses the fallback stacks, not self-hosted fontsource fonts. There is no build step to bundle them.
- **Tables:** they are plain tables, not FleetGrid. They are small dev lists, so the console is grid-exempt.
- **Icons:** unicode glyphs stand in for lucide icons. Every status still shows a word.
- **Mermaid:** vendored at `static/vendor/mermaid.min.js` (11.4.1, copied from `apps/web`'s dependency), no CDN. Diagrams render on a light `--paper` panel so the exporter's node fills read the same as on GitHub.

## UI standards (workflow studio, `apps/web`)

`apps/web` is the new React studio (pnpm package beside the Gradle build). It is written against its own contract, `apps/web/contract/openapi.yaml` (the API it wants, under `/api/v1`), never against the engine's current endpoints. Components see only the `ApiPort` interface in `apps/web/src/api`. Pointing it at a real backend means writing one new adapter. UI work in `apps/web` does not change `engine/*` or the static console.

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
