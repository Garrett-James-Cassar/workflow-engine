# Workflow studio: web UI plan

Status: **built** (2026-11-05), slices 1–7 on `main`. Scope: a new React studio in `apps/web`, a pnpm package beside the Gradle build. The engine (`engine/*`) and the static console in `engine/app/src/main/resources/static` are not changed by this plan. **Update 2026-11-06 (layout-fill, STYLE v1.5):** the studio uses `PageGrid` and the five archetypes (see UI-SPEC "Layout (v1.5)"), with a `layout` Cypress spec at three sizes. **Also:** the static console is the Demo surface (real engine, real Cypress chapters, `engine/console-e2e`); this studio is the mocked test UI against its own contract. The UI is built against its own OpenAPI contract and an in-browser mock. The engine's own Preview mode (E8) is out of scope.

The views are specified in [`../UI-SPEC.md`](../UI-SPEC.md). The binding style is `/Users/garrett.cassar/Projects/fleet/standards/ui/STYLE.md` v1.4 and its `specimen.html`. The capability rows it targets are in `/Users/garrett.cassar/Projects/fleet/standards/matrices/matrices.json`. References: `notifications-hub/apps/web` and `scheduler/apps/web`.

## 1. Users and jobs

1. **Approve or decide** a task assigned to me or my team (legal, desk head, compliance, middle office), with the outcome the workflow allows. → My tasks
2. **See where a piece of work is:** which step, who holds it, what happened so far. → Workflow runs
3. **Unblock:** retry a step that failed, fire a timeout, restart at a step. → Incidents, Workflow runs
4. **Simulate the outside world:** send the signature or confirmation event a run is waiting for. → Events
5. **Understand a workflow:** read its diagram and steps, start a run with sample data. → Workflows
6. **Move data out:** export workflows, runs and incidents (CSV and JSON). Histories are export-only.

## 2. Information architecture

| Nav item | Route | View (UI-SPEC) |
|---|---|---|
| Workflows | `/workflows` (`/` redirects), `/workflows/:name` | 1 |
| Workflow runs | `/instances`, `/instances/:id` | 2 |
| My tasks | `/tasks` | 3 |
| Events | `/events` | 4 |
| Incidents | `/incidents` | 5 |

**Desktop (≥1024px), Workflow runs:**

```
┌──────────────────┬────────────────────────────────────────────────────────────────────────┐
│ Workflow engine  │        [Acting as: Laura Jones ▾] [Demo data: changes reset…] [Run demo] │
│                  │  Workflow runs                                                          │
│ Workflows        │  Every run of a workflow, where it is and what happened.                │
│▌Workflow runs    │                                                                         │
│ My tasks         │  ┌──────────┐ ┌──────────┐ ┌──────────────┐ ┌──────────┐                │
│ Events           │  │ 14       │ │ 31       │ │ ⚠ 3          │ │ 96       │                │
│ Incidents        │  │ Running  │ │ Waiting  │ │ Failed       │ │ Completed│                │
│                  │  │          │ │          │ │              │ │ today    │                │
│                  │  └──────────┘ └──────────┘ └──────────────┘ └──────────┘                │
│                  │  [Status ▾] [Workflow ▾] [Current step …]                      Clear     │
│                  │  ┌──────────────────────────────────────────────────────────────────┐  │
│                  │  │ Runs                           [Export CSV] [Export JSON] [Columns]│  │
│                  │  ├──────────────┬────────────────────┬─────────┬──────────┬─────────┤  │
│                  │  │ Run          │ Workflow           │ Status  │ Step     │ Started │  │
│                  │  ├──────────────┼────────────────────┼─────────┼──────────┼─────────┤  │
│                  │  │ 4f2a9c1e…    │ confirmation-appr… │ ◷Waiting│ legal-re…│ 11-04…  │  │
│                  │  │ 77b0e3d2…    │ trade-booking v1   │ ✕Failed │ book-tra…│ 11-04…  │  │
│                  │  └──────────────────────────────────────────────────────────────────┘  │
└──────────────────┴────────────────────────────────────────────────────────────────────────┘
```

**Desktop, workflow view and run view:**

```
 Workflows / confirmation-approval                          Workflow runs / 4f2a9c1e
 confirmation-approval          [v1 ▾]  [■ Start run]       confirmation-approval v1   ◷ Waiting
 Approve a trade confirmation, then …                       Step: legal-review   Started 11-04 15:10
 ┌ Diagram ┬ Mermaid source ┐                               ┌ Where it is ─────────────────────┐
 │  [light panel: Mermaid]  │ [Download SVG] [PNG]          │ Open task  Legal review (legal)   │
 └─────────────────────────┘                               │ Waiting    signed, expires in 7 d │
 ┌ Steps ─────────┐ ┌ Sample variables ┐                    └───────────────────────────────────┘
 │ legal-review … │ │ { "doc": {…} }   │                    ┌ Timeline ─────────────────────────┐
 └────────────────┘ └──────────────────┘                    │ ● started   a.ops   11-04 15:10   │
                                                            │ ✓ legal-review: APPROVE  l.jones  │
                                                            └───────────────────────────────────┘
```

**390px:** the sidebar becomes a top bar with a menu button, the persona switcher moves into the menu, the figures go 2×2, and the filters collapse behind a "Filters" button. Grids stay grids and scroll horizontally inside their own box; the page never does. Under 640px `paginationPageSizeSelector: false`. Task cards stack with full-width outcome buttons (44px).

```
┌──────────────────────────────────┐
│ ☰  Workflow engine  [Demo data…] │
├──────────────────────────────────┤
│ My tasks                         │
│ Acting as Laura Jones            │
│ ┌──────────────────────────────┐ │
│ │ Legal review                 │ │
│ │ confirmation-approval · 4f2a │ │
│ │ Comment [__________________] │ │
│ │ [Approve ] [Reject ] [Counter]│ │
│ └──────────────────────────────┘ │
└──────────────────────────────────┘
```

On a coarse pointer, toolbar actions fold into one "Actions" menu button (44px) under 640px, so the toolbar never wraps. Rows are 44px on coarse pointers.

## 3. Where the code lives, how it builds and ships

`apps/web`, package `@workflow-engine/web`, modelled on `scheduler/apps/web`. It is a standalone pnpm package (its own `pnpm-lock.yaml`), so the Gradle build and `engine/*` are untouched; the Makefile ties the two gates together.

```
apps/web/
  contract/openapi.yaml                 # THE contract (OpenAPI 3.1), hand-written, source of truth
  index.html  vite.config.ts  tailwind.config.ts  postcss.config.js  tsconfig.json
  cypress.config.ts  cypress/{e2e/smoke,e2e/features,e2e/demo,support}
  scripts/{cypress.mjs,share.mjs,check-style.mjs}
  src/
    api/generated/schema.d.ts           # openapi-typescript output (pnpm gen:api), committed
    api/port.ts                         # ApiPort: the only API surface components see
    api/http-adapter.ts                 # ApiPort over openapi-fetch (live, and mock/demo through MSW)
    api/index.tsx                       # picks the adapter; ApiProvider/useApi
    mocks/mother.ts                     # Object Mother, fixed seed (mulberry32)
    mocks/sim.ts                        # table-driven simulator over the exported graph data
    mocks/store.ts  mocks/routes-*.ts   # in-memory backend: paging, sort, filter, mutations
    mocks/handlers.ts  mocks/browser.ts # MSW wrappers: latency 150–400 ms, ?scenario=
    mocks/contract.test.ts              # every mock response validated against openapi.yaml
    lib/{access,format,mode,persona,cn}.ts
    components/ui/*  components/grid/*  components/states/*  components/{Layout,ModeBanner,...}
    routes/*                            # one folder per view
    demo/                               # Demo button + chapters; demo/vendor = rules-engine runtime
```

- **Contract first.** `contract/openapi.yaml` describes the API this UI wants (section 4). `pnpm gen:api` runs `openapi-typescript` → `src/api/generated/schema.d.ts`. `make ci` regenerates it and fails on a diff.
- **ApiPort.** `src/api/port.ts` is a small interface: `listWorkflows`, `getWorkflow`, `workflowDiagram`, `startInstance`, `listInstances`, `instanceStats`, `getInstance`, `instanceEvents`, `retry`, `restart`, `fireTimeout`, `listTasks`, `completeTask`, `reassignTask`, `listWaits`, `sendEvent`, `listIncidents`, `people`, and the three exports. Components import only `useApi()` and the generated types, never fetch or MSW. `HttpApiAdapter` implements it with `openapi-fetch` and turns non-2xx problem+json into a typed `ApiProblem`. A different backend means one new adapter file.
- **Profiles** (`VITE_MODE`):
  - **`mock`** (default; `make web`): MSW serves every contract operation from `mocks/store.ts`. The data is seeded by the Object Mother (fixed seed): the four sample workflows, about 250 runs on the main grid across running, waiting, completed, ended and failed, each with a believable event history, open tasks and waits, and the engine's dev personas. The store honours offset/limit/sort/filter. Mutations persist until reload. Latency is 150–400 ms, seeded. `?scenario=empty|error|slow`.
  - **`demo`** (`pnpm dev:demo`, and the static demo build): the same mock, reset on every load, plus the mode banner and the Demo button that replays the smoke and main-flow specs in this tab using rules-engine's in-page Cypress runtime, vendored under `src/demo/vendor`.
  - **`live`** (`dev:live`): `HttpApiAdapter` against `VITE_API_BASE_URL` (default `''`; in dev Vite proxies `/api` → `VITE_PROXY_TARGET`, default `http://localhost:47831`). The engine does not serve `/api/v1` yet, so live shows the STYLE.md error state. That is expected until the contract is implemented or fronted by an adapter.
  - MSW is imported lazily, so it never ships in a live build.
- **The simulator is table-driven, not a second engine.** `mocks/sim.ts` reads the exported graph JSON (`docs/workflows/*.graph.json`, regenerated by `./gradlew diagrams` and kept fresh by `DiagramsUpToDateTest`) and follows the edge whose `outcome` matches. Service tasks and decisions advance through one small TABLE (`step id → rule`), for example `size-check: notional > 50m → LARGE`, `book-trade: quantity ≤ 0 → REJECTED`. User tasks park the run, event waits park it with their correlation and timeout, `end:*` nodes end it. No traversal rules beyond "follow the edge for this outcome". The Mermaid diagram fixtures are the committed `docs/workflows/*.mmd` files (the exporter's output).
- **Dates and times** are formatted only in `lib/format.ts`, per STYLE.md v1.4. The contract is ISO 8601 UTC.
- **Dev port 5183** (`strictPort`); preview 5183; the fleet stack serves the demo build on **15183**.
- **Gates.** `make ci` = Gradle `check` (core + app, 60 tests) plus the web gate: `web-gen-check`, `web-typecheck`, `web-lint` (style grep: no hex outside tokens, no `uppercase`), `web-test` (vitest, including the contract test), `web-build`, `web-cy` (all Cypress specs mocked, coverage ≥ 80%), `web-demo-check` (every chapter played unpaced).
- **Demo build for the fleet stack:** `pnpm --filter ./apps/web build:demo` (from the repo root: `pnpm -C apps/web build:demo`) = `vite build --mode demo`, which writes `apps/web/dist/` containing `index.html` and `mockServiceWorker.js`. Static, no backend. The coordinator adds it to `fleet/compose.yaml` on `127.0.0.1:15183`.
- **Two URLs per app:** the UI (`:5183`, demo `:15183`) and the engine's Swagger UI at `http://localhost:47831/docs` (springdoc, already served).

## 4. The contract and the gaps against the real API

All paths are under `/api/v1`, all times are ISO 8601 UTC, and every error is `application/problem+json` (`type, title, status, detail, instance, traceId, errors[]`).

| Operation | Contract | Real API today | Gap |
|---|---|---|---|
| list workflows | `GET /workflows` → `{items,total}`, `offset,limit,sort,order,name` | `GET /definitions` (array, no paging) | GAP: paging, sort, filter |
| workflow | `GET /workflows/{name}?version=` | `GET /definitions/{name}` | ok: /api/v1 now served |
| diagram | `GET /workflows/{name}/diagram?version=` (text/plain Mermaid) | `GET /definitions/{name}/export?format=mermaid` | GAP: path |
| start run | `POST /workflows/{name}/instances` | `POST /definitions/{name}/instances` | ok: /api/v1 now served |
| list runs | `GET /instances` → `{items,total}`, `status` (multi), `workflow`, `step`, sort, order | `GET /instances?limit=` (array, no `total`) | GAP: paging, sort, filters, `total` |
| run fields | `startedAt`, `updatedAt` on every run | not exposed | GAP: timestamps |
| stats | `GET /instances/stats` | none | GAP: new endpoint |
| run | `GET /instances/{id}` | same, unversioned | ok: /api/v1 now served |
| events | `GET /instances/{id}/events` | same | ok: /api/v1 now served |
| retry | `POST /instances/{id}/retry` | same | ok: /api/v1 now served |
| restart | `POST /instances/{id}/restart` | same | ok: /api/v1 now served |
| fire timeout | `POST /instances/{id}/timeouts/{step}` | `POST /timeouts/{instanceId}/{step}` | GAP: path |
| tasks | `GET /tasks?mine=` → `{items,total}`, paging | `GET /tasks` (array) | GAP: paging |
| complete | `POST /tasks/{id}/complete` | same | GAP: prefix; 403 `reason` becomes problem+json `detail` |
| reassign | `POST /tasks/{id}/reassign` | same | ok: /api/v1 now served |
| waits | `GET /waits` → `{items,total}` | none (waits only inside a run) | GAP: new endpoint |
| send event | `POST /events/{name}` | same | GAP: prefix; 404 becomes problem+json |
| incidents | `GET /incidents` → `{items,total}` | none (derive from failed runs + `incident` events) | GAP: new endpoint |
| people | `GET /people` | `GET /rbac/people` | GAP: path (dev aid until central RBAC) |
| exports | `GET /workflows/export`, `/instances/export`, `/incidents/export` (portable document) | none | GAP: new endpoints (pt.2) |
| errors | problem+json with `traceId` | `ErrorView {error, reason}` | GAP: api.2 |
| trace | `X-Trace-Id` header | none | GAP: obs.2 |
| Swagger UI | `/docs` | served (springdoc) | none |

The contract test (`src/mocks/contract.test.ts`, part of `make ci`) walks every operation in the YAML, calls the mock for its success and error cases, and validates status, content type and body against the response schema (ajv, JSON Schema 2020-12). It fails if an operation has no mock. The contract is the brief for a backend facade; `docs/contract-gaps.md` is written when a backend e2e suite is synced to it (`/contract-sync`).

## 5. Delivery slices (one commit each, `make ci` green after each)

**Slice 1: shell + Workflows grid, mock and demo profiles.**
- The contract (all operations, so it is reviewed once), generated schema, ApiPort + HttpApiAdapter, Object Mother + store + simulator + MSW + scenarios, contract test.
- Tokens + fonts, shell (sidebar, top bar, mode banner, persona switcher, 404), FleetGrid + `fleetGridTheme` + infinite datasource, view 1 list (read-only: name filter, sort, four states), Demo button (vendored runtime), lint/style grep, `make web` / `make web-cy`, lefthook pre-push, one smoke spec with axe.
- Specs: `smoke/workflows.cy.ts` (grid, filter, open a workflow, axe, empty and error scenarios).
- Moves to yes: ui.0, ui.2, ui.5 (view 1), t.e2e.b, t.e2e.c, t.e2e.d, t.e2e.f, rp.1, a11y.1, api.1 (Swagger at `/docs` is already live).

**Slice 2: workflow view (diagram, steps, sample vars), Start run, Workflow runs list and run view.**
- Specs: `smoke/workflow.cy.ts`, `smoke/instances.cy.ts`, `features/start-run.cy.ts`, `features/instances-filters.cy.ts`.
- Moves to yes: ui.1 (§10 pass), a11y.2, a11y.5.

**Slice 3: My tasks, persona switcher, complete with outcome buttons, 403 reason, reassign.**
- Specs: `smoke/tasks.cy.ts`; `features/complete-task.cy.ts` (blocked persona, then the right one; reject → resubmit → approve), `features/reassign.cy.ts`.
- Moves to yes: t.e2e.e, a11y.4.

**Slice 4: Events and Incidents; Retry, Fire timeout, Restart.**
- Specs: `smoke/events.cy.ts`, `smoke/incidents.cy.ts`; `features/send-event.cy.ts`, `features/retry.cy.ts`, `features/timeout.cy.ts`.
- Moves to yes: ui.5 (all), a11y.3.

**Slice 5: exports.**
- Specs: `features/export.cy.ts` (CSV and JSON on each grid).
- Moves to yes: pt.4. pt.2, pt.3, pt.5 stay backend criteria: satisfied in the contract only; imports are not offered (workflows are code, histories are append-only).

**Slice 6: coverage gate, mobile, errors, reduced motion.**
- `@cypress/code-coverage` + `vite-plugin-istanbul` with an 80% line gate.
- Specs: `smoke/mobile.cy.ts` (390px); `features/errors.cy.ts` (problem+json title, detail, trace ID, Retry; live unreachable).
- Moves to yes: t.e2e.a, a11y.6.

**Slice 7: the seven Demo chapters, demo checks, share.**
- The chapters (below), `demo/chapters.cy.ts`, `demo/demo-a11y.cy.ts`, `make share`, `cy:live` + `.github/workflows/e2e-live.yml`.
- Moves to yes: demo.1–demo.8, demo.9 (attested), t.e2e.f.

## 6. Test plan

- **Smoke** (`cypress/e2e/smoke/`, run on pre-push against the mock profile): `workflows`, `workflow`, `instances`, `tasks`, `events`, `incidents`, `mobile`. Each under 30 s. `cy.a11y()` (cypress-axe; serious and critical fail) after each distinct screen state; a no-op in the Demo runtime.
- **Features** (`cypress/e2e/features/`): `start-run`, `instances-filters`, `complete-task`, `reassign`, `send-event`, `retry`, `timeout`, `export`, `errors`.
- **Determinism (t.e2e.d).** Every `cy.visit` loads a fresh store from the fixed-seed mother. Specs assert on structure and text, not wall-clock values. Seeded waits expire at least 10 min ahead, except the trade-booking wait the timeout chapter fires by hand. Latency is on in Cypress (it proves the loading states). No `cy.wait(ms)`. `TZ` is pinned to `UTC`.
- **Coverage (t.e2e.a).** istanbul only when `CYPRESS_COVERAGE=true`; `nyc check-coverage --lines 80` over `src/` excluding `mocks/`, `demo/vendor/` and `api/generated/`.
- **Three ways to run them** (TESTING.md): `cy:run` headless mocked (the gate); `cy:live` against the engine on 47831 and Postgres 55434 (opt-in; nightly `.github/workflows/e2e-live.yml`; specs the engine cannot satisfy yet are skipped as counted `GAP:`s); the Demo button.
- **Unit and contract.** vitest: `mocks/contract.test.ts`, the simulator, `format`, `problem`, and store paging/sort/filter.
- **Backend e2e** is the existing Testcontainers suite (`./gradlew check`), unchanged.
- **data-cy convention.** `<view>-<thing>[-<qualifier>]`, kebab-case: `nav-workflows`, `nav-instances`, `nav-tasks`, `nav-events`, `nav-incidents`, `mode-banner`, `demo-start`, `menu-toggle`, `persona-switch`; grids `workflows-grid`, `instances-grid`, `waits-grid`, `incidents-grid`; `status-badge` (`data-status`); `task-card`, `task-outcome-<outcome>`; `state-loading`, `state-empty`, `state-error`, `state-error-trace-id`, `state-retry`; `toast`.

### The seven Demo chapters

Each chapter is one `it()` of a real Cypress spec in `cypress/e2e/{smoke,features}`, starts from the fixed-seed mock, and stays inside the player's command subset (no `cy.viewport`, `cy.clock`, `cy.intercept`).

1. **Browse a workflow and its diagram** (`smoke/workflow`): Workflows → confirmation-approval → Diagram, Mermaid source.
2. **Start a run with sample vars** (`features/start-run`): Start run with the sample, land on the run, Waiting at Legal review.
3. **A non-eligible persona is blocked, then the right persona approves** (`features/complete-task`): act as `a.ops`, Approve on Legal review → "Not allowed" with the engine's reason; switch to `l.jones`, Approve.
4. **Reject → resubmit → approve** (`features/complete-task`): `l.jones` Reject, `a.ops` Resubmit on Amend draft, `l.jones` Approve.
5. **A correlated event completes the run** (`features/send-event`): desk-head sign-off, then Events → `signed` with the envelope correlation → Completed.
6. **A timeout takes the TIMED_OUT path** (`features/timeout`): start trade-booking, run waits at Await matched confirmation, Fire timeout → chased, task "Unmatched: decide".
7. **Incident → retry** (`features/retry`): Incidents → book-trade failed → Retry step → run moves on.

Fresh seed per chapter (`resetData`), ring and captions, reduced motion, and CI plays every chapter unpaced (`runChapterForTest`, demo.7). `make share` serves the demo build through ngrok with `NGROK_AUTHTOKEN` from env; the remote viewer runs the Cypress runtime in their own tab (demo.9).

## 7. STYLE.md §10 conformance walk-through

| Item | How it is met |
|---|---|
| Tokens only | `src/index.css` holds the §2 block verbatim. `tailwind.config.ts` maps tokens following the reference apps plus `success`, `warning`, `info`, `primary-soft`, `text-figure`, `text-title`. `scripts/check-style.mjs` (run by `web-lint`) fails on `#hex`, `rgb(`, named colours and `uppercase` outside `index.css` and the diagram panel. |
| Typefaces, mono, scale, no caps | `@fontsource/fraunces`, `@fontsource/hanken-grotesk`, `@fontsource/jetbrains-mono` are self-hosted. One `<Mono>` component (ligatures off) for IDs, step ids, correlation values and JSON only. The Tailwind font-size scale is replaced with the §3 steps. |
| Button variants | The six variants are typed in `button.tsx`. The single `default` per view: Start run (workflow view), Retry step (run view when failed; Incidents row detail), Send event (Events). None on My tasks, Workflows list and Workflow runs list. `destructive-solid` only in confirm dialogs (the studio has none today). |
| Status = icon + word | `Badge` takes a status and renders colour, icon and word from one map, which is the UI-SPEC **Statuses** table. |
| Dates and times | One `<Time>` and `format.ts`: local `2026-11-04 15:10`, exact UTC on hover, relative only where timing is the point (wait expiry, last incident). No date inputs. |
| Focus and tap targets | Global `:focus-visible` ring. Under `pointer: coarse`, controls ≥ 44px and icon buttons get an `::after` hit area; checked in `smoke/mobile`. |
| Four states + mode banner | `LoadingState`, `EmptyState`, `ErrorState` are required props of every data route. `ModeBanner` in the shell for mock and demo. `?scenario=` makes each reachable. |
| FleetGrid or exempt | Workflows, Workflow runs, Waiting events and Incidents use FleetGrid. The workflow view, run view and Events form are exempt (one record or a form). My tasks is exempt: a card inbox where each item carries its own form and buttons. |
| Copy | All copy is in UI-SPEC, reviewed against §7: verbs on buttons with matching toasts, no apologies, sentence case, no middle dots. |

**a11y matrix:** a11y.1 axe in every smoke spec; a11y.2 all actions are buttons or links, AG Grid keyboard navigation, Enter on a row opens it, dialogs trap focus and Esc closes; a11y.3 only measured token pairs, axe contrast in smoke; a11y.4 ≥ 44px on coarse pointers; a11y.5 `<nav>`, `<main>`, `<header>`, one `h1`, `aria-label` on icon buttons, real `<label for>`; a11y.6 transitions and skeleton pulse off under `prefers-reduced-motion`.

**Other rows:** rp.1 demo profile with a seeded in-memory store and a Demo button; rp.2 the engine's local profile is already Postgres in Docker (`make dev`); pt.4 export from every grid (import is not offered, see slice 5); api.1 Swagger at `/docs`; api.2 problem+json in the contract and the error states (the engine itself is a gap); arch.6 the demo build needs no backend and the live profile fails closed to the error state with the engine and UI still healthy.

## 8. As built: deviations from the plan

- **Tasks:** the "Everyone's tasks" switch is the URL parameter `all=1`; `instance=<id>` narrows to one run (the run view's "Open in My tasks" link).
- **Events:** a run's wait card links to `/events?instance=<id>`, which fills the form from that run's first wait. The waits grid has CSV export only (no portable JSON: waits are live state, not a history).
- **Incidents:** a row opens a dialog with the full message and Retry step; the run's own page also has Retry step.
- **Stats:** `completedToday` is the last 24 hours, so the figure never reads zero just after midnight.
- **Unit runner:** vitest (the repo has no other JS runner). The contract test is part of `make ci`.
- **Live check:** `make web-live-check` (`cy:unreachable`) proves the live profile fails closed to the STYLE.md error state with no mode banner when nothing serves the contract.
- **`cy:live`:** the engine does not serve `/api/v1`, so every spec is skipped and counted as a `GAP:` (exit 0), as designed.

## 9. Risks and open questions

| # | Risk or question | Recommendation |
|---|---|---|
| R1 | **Contract vs reality.** The engine serves unversioned paths, no paging, `ErrorView` errors and no stats, waits, incidents or exports. Live mode has no target. | Treat `contract/openapi.yaml` as the backend brief. Build either a Spring `/api/v1` facade controller, or implement the contract in the engine as its own task (it also fixes api.2/api.3). Not done here. |
| R2 | **Mermaid in the browser** adds ~1 MB. | Import `mermaid` lazily on the workflow view, pinned to 11.4.1 and self-hosted (no CDN), so the demo works offline. |
| R3 | **Vendored demo runtime** (~900 lines from rules-engine) is outside the lint gate. | Record it under CLAUDE.md "Fleet deviations"; lift it into a fleet package when a third app needs it. |
| R4 | **Simulator drift:** the table of service-task and decision outcomes could diverge from the Kotlin handlers. | Keep the table tiny and keyed by step id; `contract.test.ts` plus a unit test assert every step in every graph has a rule or a declared outcome path. |
| R5 | **Persona is a dev aid:** the engine trusts `X-User`. | Keep the switcher in `sessionStorage`, label it "Acting as", replace with the central RBAC session when it lands (ui.3/ui.4). |
| R6 | **Two UIs side by side** (static console and studio) on the same engine. | Superseded 2026-11-06: the console stays as the Demo surface and is not retired; the studio is the mocked test UI. |
| Q1 | Where does `/api/v1` live: engine facade or a gateway? | Engine facade controller, behind a flag, in a separate task. |
| Q2 | The coordinator named "two sample workflows"; there are four. | Seed all four; chapters use `confirmation-approval` and `trade-booking`. |
| Q3 | Import? | Not offered: workflows are Kotlin code (E7 brings export/import), histories are export-only. |
| R7 | The AG Grid sort-indicator glyph renders as a grey square in headless Electron (Cypress); it comes from `fleetGridTheme` copied unchanged from the reference apps. Not verified in Chrome. | Check in a real browser; if it persists, fix once in the shared theme, not here. |
| R8 | The static demo bundle includes the Cypress specs the Demo replays (about 150 kB). | Accepted: it is what makes the chapters the real specs. Live builds never load them. |
