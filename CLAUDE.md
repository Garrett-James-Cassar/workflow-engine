# Working in workflow-engine

- `engine/core` must stay pure Kotlin (no Spring, no I/O). `Engine` is a pure transition function; the app applies its effects.
- Every step yields an enum outcome. Routing is `on { when (it) { … } }`, and the compiler's exhaustiveness check is the feature: never add `else` branches to sample workflows.
- New workflow: put the DSL `object : Workflow(...)` in `engine/app/.../app/workflow/` and list it in `Workflows.kt`; linked handler code lives in `app/samples`, then run `./gradlew diagrams` (DiagramsUpToDateTest fails otherwise). A behaviour change needs a version bump.
- Tests: core uses kotlin.test; compile-fail cases live in `CompileFailTest` (in-process kotlinc). App e2e uses Testcontainers Postgres plus a `MutableClock`. Prefix test names with the milestone (`E5 …`).
- Gate: `make ci` (`./gradlew check`).

## Fleet deviations (UI)
The console in `engine/app/src/main/resources/static` is a no-build page, ahead of the planned React studio (S1–S8):
- **Fonts:** it uses the fallback stacks, not self-hosted fontsource fonts. There is no build step to bundle them.
- **Tables:** they are plain tables, not FleetGrid. They are small dev lists, so the console is grid-exempt.
- **Icons:** unicode glyphs stand in for lucide icons. Every status still shows a word.
- **Mermaid:** vendored at `static/vendor/mermaid.min.js` (11.4.1, copied from `apps/web`'s dependency), no CDN. Diagrams render on a light `--paper` panel so the exporter's node fills read the same as on GitHub.

## UI standards (workflow studio, `apps/web`)

`apps/web` is the new React studio (pnpm package beside the Gradle build). It is written against its own contract, `apps/web/contract/openapi.yaml` (the API it wants, under `/api/v1`), never against the engine's current endpoints. Components see only the `ApiPort` interface in `apps/web/src/api`. Pointing it at a real backend means writing one new adapter. UI work does not change `engine/*` or the static console.

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
