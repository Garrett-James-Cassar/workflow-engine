# Workflow engine: UI spec

Criterion **ui.2**. The views of the workflow studio in `apps/web` (React, Vite, TypeScript, Tailwind). Every view follows the fleet style guide (`/Users/garrett.cassar/Projects/fleet/standards/ui/STYLE.md` v1.4).

The studio is written against its own contract, [`apps/web/contract/openapi.yaml`](apps/web/contract/openapi.yaml): the API it *wants*, under `/api/v1`. It is **not** the engine's current unversioned API, which is unchanged (gaps are listed in [`docs/ui-plan.md`](docs/ui-plan.md) §4). Components see only the `ApiPort` interface (`apps/web/src/api`). The `mock` and `demo` profiles serve the contract from an in-browser mock. The studio is the app's only UI, and its mocked build is the Demo (see "Demo chapters" at the end).

**Who uses it:** people who run business approvals on the engine: approvers (legal, desk head, compliance) working their task inbox, operations and middle office watching instances and clearing incidents, and workflow authors checking what a workflow does. The engine's own Preview mode (E8) is out of scope.

**Words used on screen** (STYLE.md §7). The API names on the left stay in code and in "Raw" tooltips only.

| API value | On screen |
|---|---|
| definition | Workflow |
| instance | Workflow run |
| `userTask` / task | Task |
| `eventWait` / wait | Event wait |
| outcome (`APPROVE`) | the outcome word in sentence case on its button: "Approve", "Reject", "Counter" |
| incident (`status: failed`) | Incident |
| `endStatus` | Ended as |
| `X-User` | Acting as |

**Permissions.** Every operation names a permission and is shown only when `can(permission)` is true. `can()` is one seam in `src/lib/access.ts`. It answers from `GET /api/v1/me` (the caller's permissions, asked again when the acting person changes), with the engine's own dotted names, so hidden in the UI also means refused by the API. Nothing is drawn until the answer arrives (`AccessGate`); if the call fails, nothing privileged shows. The engine's own checks (task roles, reassign, support restart) still apply and surface as a 403 with a reason. Where a person lacks the permission for a whole view, its sidebar entry is hidden, and opening the address anyway shows the 403 reason ("workflows.read is needed"). In the mock, `/me` and every 403 come from a copy of the engine's persona file (`src/mocks/personas.json`, kept equal to `engine/app/src/main/resources/rbac/workflow-engine.json` by `src/mocks/rbac.test.ts`) and a route-to-permission table (`src/mocks/permissions.ts`).

| Permission | Allows |
|---|---|
| `workflows.read` | the Workflows list and Workflow views |
| `workflows.export` | Export all and a row's Export on workflows; Export CSV; diagram download; Export configuration |
| `workflows.import` | Import on workflows; Import configuration |
| `instances.read` | the Workflow runs list and Workflow run views |
| `instances.start` | Start run |
| `instances.retry` | Retry step |
| `instances.restart` | Restart at step (workflow-support) |
| `instances.fire-timeout` | Fire timeout |
| `instances.export` | Export all and a row's Export on runs; Export CSV |
| `tasks.read` | My tasks |
| `tasks.complete` | the outcome buttons on a task (a task assigned to the person needs none) |
| `tasks.reassign` | Reassign task |
| `tasks.export` | (no studio screen: tasks are a card inbox; the engine route exists) |
| `waits.export` | Export all and a row's Export on the waiting events; Export CSV |
| `events.send` | Send event |
| `events.export`, `people.export` | (no studio screen; the engine routes exist) |
| `incidents.read` | the Incidents view |
| `incidents.retry` | Retry step on an incident |
| `incidents.export` | Export all and a row's Export on incidents; Export CSV |
| `audit.read` | the Audit view (sidebar entry and `/audit`): time, actor, via, action, resource, outcome, changed field names; filters actor, action, resource; read only, no row menu |
| `audit.export` | Export all on the Audit view |

**Statuses** (STYLE.md v1.4 §6, Domain statuses). Every one is a Badge with its colour, icon and word. Map by meaning: finished and fine is success, waiting on a person or an event is warning, a failed step is destructive, and inert history is neutral.

| Applies to | API value | Word | Colour | lucide icon |
|---|---|---|---|---|
| run | `running` | Running | info | `play` |
| run | `waiting` | Waiting | warning | `clock` |
| run | `completed`, `endStatus` completed or published | Completed | success | `check` |
| run | `completed`, any other `endStatus` | Ended | neutral | `flag` |
| run | `failed` | Failed | destructive | `x-circle` |
| task | `open` | Open | warning | `clock` |
| task | `done` | Done | success | `check` |
| timeline event | `instance-started`, `step-entered`, `restarted`, `retried` | the event word | neutral | `circle-dot`, `arrow-right`, `rotate-ccw`, `rotate-cw` |
| timeline event | `step-completed` | the step and outcome | success | `check` |
| timeline event | `incident` | Incident | destructive | `x-circle` |
| timeline event | `instance-ended` | Ended | neutral | `flag` |
| workflow | has a newer version | (version chips, no status) | n/a | n/a |

**Dates and times** (STYLE.md v1.4 §6). The API sends and receives ISO 8601 UTC (`Z`); formatting happens only in the UI (`src/lib/format.ts`).
- **Default:** the viewer's local time as `2026-11-04 15:10`. Hover or long press shows exact UTC: `2026-11-04 14:10:07 UTC`.
- **Relative, where timing is the point:** an event wait's expiry ("expires in 4 min") and "last incident". The absolute local time goes in the tooltip, and it switches to absolute after 7 days.
- **Not mono.** Timestamps are prose. Run IDs, task IDs, step ids, correlation values and JSON are mono with ligatures off.
- There are no date inputs: the studio has no date fields. If one is needed later, it uses the shared `DateTimeField`.

**Acting as from the address** (mock and demo): `?as=<login>` opens the page as that person, because a page load otherwise returns to the default persona.

**Scenarios** (mock and demo): `?scenario=empty` returns no data, `?scenario=error` makes every call answer with a 503 problem+json, and `?scenario=slow` adds 4 s of latency.

**Every error state** renders the API's problem+json (`title` → heading, `detail` → cause), plus Retry and the trace ID in mono (STYLE.md §6, States).

**Acting as.** The engine identifies a caller by the `X-User` header. In mock and demo the top bar has an "Acting as" persona switcher (`GET /api/v1/people`: login, name, teams). It is kept in `sessionStorage`, defaults to `e.admin` (Erin Admin, the engine admin: a first visit shows every screen and action), and is sent as `X-User` on every request. Switch to a narrower persona to see what that role sees ("Who sees what" below). In live mode the same switcher is shown as a dev aid, because the engine trusts the header until the central RBAC service lands.

---

## 1. Workflows

**Purpose:** see which workflows exist, read their diagram, and start a run.

**Route:** `/workflows`, and `/workflows/:name` for one workflow (`?version=` selects an older version).

**Who sees what** (matrix below): requester, workflow support (to pick a restart step) and auditor see the view; the admin also sees Export all, Import and Download SVG; requester and admin see Start run.

**Data shown, list** (`GET /api/v1/workflows`, infinite row model over `offset` + `limit` with `total`, server-side `sort` + `order`):

| Column | Field | minWidth | Notes |
|---|---|---|---|
| Workflow | `name` | 240 | mono; links to the workflow view |
| Latest version | `latestVersion` | 120 | number, right-aligned |
| Versions | `versions` | 160 | "1, 2" |
| Steps | `steps` | 96 | number, right-aligned |

- **Filter:** Name (contains).

**Data shown, workflow view** (`GET /api/v1/workflows/{name}`, `…/diagram`):
- **Header:** title = the workflow name (`text-title`, Fraunces). Version chips (select an older version). Breadcrumb "Workflows / confirmation-approval".
- **Diagram card:** the Mermaid diagram rendered from `GET /api/v1/workflows/{name}/diagram` (the engine's `MermaidExporter` output), on a light panel. Tabs: Diagram, Mermaid source (mono). "Download SVG" and "Download PNG" are outline buttons.
- **Steps card:** a definition list of the steps: id (mono), kind, who holds it (role, user, or "from vars"), and the outcomes it can produce.
- **Sample variables card:** the workflow's `sample {}` vars as mono JSON, read-only. They pre-fill Start run.

**Operations**

| Button | Variant | Permission | Result |
|---|---|---|---|
| Start run | default (the one per view, on the workflow view) | `instances.start` | dialog with the vars as editable mono JSON (pre-filled from the sample); `POST /api/v1/workflows/{name}/instances`; toast "Run started", then the run view |
| Export CSV | outline (grid toolbar) | `workflows.export` | AG Grid CSV of the loaded rows; toast "Export started" |
| Export all | outline (grid toolbar) | `workflows.export` | `GET /api/v1/workflows/export`: every workflow as a document in the fleet envelope; toast "Export started" |
| Import | outline (grid toolbar) | `workflows.import` | the import flow below; toast "Imported: N created, M updated" |
| Export (row menu) | menu item | `workflows.export` | `GET /api/v1/workflows/{name}:{version}/export` |
| Download SVG, Download PNG | outline | `workflows.export` | client-side download of the rendered diagram |
| Clear filters | ghost | `workflows.read` | resets the query string |

Workflows are the one Config kind (docs/RESOURCES.md). A workflow written in Kotlin is the engine's own: importing a document that differs from it is a Conflict and is skipped. An imported document is stored and exported again, but it does not run (its decisions and handlers are code).

**Grid:** the list is FleetGrid (`fleetGridTheme`), with Export all, Import and a row menu ("⋯", Export). The workflow view is grid-exempt: one record, shown as a diagram and definition lists.

**States**
- Loading: skeleton rows in the grid frame; a diagram-shaped skeleton on the workflow view.
- Empty (no workflows): headline "No workflows registered". Text: "Workflows are defined in Kotlin and registered when the engine starts. Add one to Workflows.kt to see it here." No action (nothing to create in the UI).
- Empty (filter): headline "No workflows match this name", with Clear filters.
- Error: heading "Could not load workflows". Cause from `detail`, Retry, trace ID.
- Error 404 (workflow view): heading "Workflow not found". Cause "No workflow is registered as `x`." Action "Back to workflows".

## 2. Workflow runs

**Purpose:** see every run, where it is, and what happened; unblock the ones that stopped.

**Route:** `/instances` (list) and `/instances/:id` (one run). Filters live in the query string (`?status=waiting&workflow=trade-booking`).

**Who sees what** (matrix below): requester, incident handler, workflow support, auditor and admin see the view; incident handler and admin see Retry step; workflow support and admin see Restart at step; only the admin sees Fire timeout and Export all.

**Data shown, list** (`GET /api/v1/instances`, infinite row model, as on view 1):
- **Key figures** (`GET /api/v1/instances/stats`): Running, Waiting, Failed (coral with an `alert-triangle` icon when above 0, since that number *is* a status), Completed in the last 24 hours (`completedToday` in the contract: the last 24 hours, so it never reads zero just after midnight).
- **Grid:**

| Column | Field | minWidth | Notes |
|---|---|---|---|
| Run | `id` | 200 | mono; links to the run view; short UUID |
| Workflow | `definition` | 200 | with version, e.g. "confirmation-approval v1" |
| Status | `status`, `endStatus` | 140 | Badge per the Statuses table, "Ended as withdrawn" in the tooltip |
| Current step | `currentStep` | 200 | mono; empty when ended |
| Ended as | `endStatus` | 140 | hidden by default |
| Started | `startedAt` | 160 | absolute local, UTC on hover; default sort descending |
| Updated | `updatedAt` | 160 | hidden by default |

- **Filters:** Status (multi-select), Workflow, Current step (contains).

**Data shown, run view** (`GET /api/v1/instances/{id}`, `…/events`):
- **Header:** title = the workflow name and version; the run ID (mono, copy button). Status badge, the current step, "Started 2026-11-04 15:10". Breadcrumb "Workflow runs / 4f2a…".
- **Where it is card:** the open tasks (assignee, outcomes), the open event waits (event, correlation, "expires in 4 min") and, for a failed run, the incident message in mono.
- **Timeline** (events, oldest first): the event word and badge, the step (mono), outcome, actor, local time (UTC on hover), and an expandable payload.
- **Variables card:** the run's vars as read-only mono JSON.

**Operations**

| Button | Variant | Permission | Shown when | Result |
|---|---|---|---|---|
| Retry step | default | `instances.retry` | failed | `POST /api/v1/instances/{id}/retry`; toast "Step retried" |
| Fire timeout | secondary | `instances.fire-timeout` | waiting on an event wait with a timeout | `POST /api/v1/instances/{id}/timeouts/{step}`; toast "Timeout fired" |
| Restart at step | outline | `instances.restart` | waiting on a user task | dialog to choose the step, `POST /api/v1/instances/{id}/restart`; a 403 shows the engine's reason; toast "Run restarted" |
| Export CSV | outline (grid toolbar) | `instances.export` | list | CSV of loaded rows; toast "Export started" |
| Export all | outline (grid toolbar) | `instances.export` | list | `GET /api/v1/instances/export` with the filters; toast "Export started" |
| Export (row menu) | menu item | `instances.export` | list | `GET /api/v1/instances/{id}/export` |
| Clear filters | ghost | `instances.read` | list | resets the query string |
| Copy run ID | ghost icon (`copy`, aria-label) | `instances.read` | always | toast "Run ID copied" |

Run histories are append-only, so they are export-only (no import).

**Grid:** the list is FleetGrid, infinite row model. The run view is grid-exempt: a single record with a timeline of 4–30 events that people read top to bottom, not scan, sort or filter.

**States**
- Loading: four key-figure skeletons and nine skeleton rows.
- Empty (none): headline "No runs yet". Text: "A run is one workflow in progress. Start one from a workflow to see it here." Action: Start a run (opens Workflows, secondary).
- Empty (filter): headline "No runs match these filters", with Clear filters.
- Error: heading "Could not load workflow runs". Cause, Retry, trace ID.
- Error 404 (run view): heading "Run not found". Action "Back to workflow runs".
- Error 409 on an action (another user acted first): toast "This run already moved on, so the step was not retried", and the page refreshes.

## 3. My tasks

**Purpose:** do the approvals and decisions assigned to you.

**Route:** `/tasks`. The "Acting as" persona in the top bar decides whose tasks show. A switch "Everyone's tasks" lists all open tasks (`mine=false`) for ops.

**Who sees what** (matrix below): the requester (to find the task assigned to them), legal (and the other approver roles), ops, auditor and admin see the view and Everyone's tasks; legal and admin see the outcome buttons (the requester only on a task assigned to them, which needs no permission to complete); ops and admin see Reassign task.

**Data shown** (`GET /api/v1/tasks?mine=true`, paged):
- One card per task: the step label as `text-lg`, the workflow and run (mono, link), held by (a user, or a team with its scope badge e.g. `counterparty: ACME`), created time.
- **The form:** one field per form field (text, number, boolean checkbox), required marked; mono is not used.
- **One button per outcome** (`outcomes[]`), in the order the workflow declares them: "Approve", "Reject", "Counter". There is no `default` variant here; the first outcome is `secondary`, the rest `outline`, because no outcome is the one the page exists for.

**Operations**

| Button | Variant | Permission | Result |
|---|---|---|---|
| <Outcome> (one per `outcomes[]` value) | secondary / outline | `tasks.complete` | `POST /api/v1/tasks/{id}/complete` with `{outcome, form}`; toast "Task completed: Approve". A 403 shows inline on the card: the engine's `reason` in plain words under a coral "Not allowed" heading. |
| Reassign task | ghost | `tasks.reassign` | dialog (user or team, scope); `POST /api/v1/tasks/{id}/reassign`; toast "Task reassigned" |
| Everyone's tasks | switch | `tasks.read` | lists all open tasks |

**Grid:** grid-exempt. A task inbox is a short list of cards that each carry their own form and outcome buttons; people act on them one by one rather than scan, sort or filter them. The "Everyone's tasks" mode uses the same cards.

**States**
- Loading: three card skeletons.
- Empty: headline "Nothing waiting for you". Text: "Tasks appear here when a workflow needs a decision from you or your team." Action: "Open workflow runs" (secondary).
- Error: heading "Could not load your tasks". Cause, Retry, trace ID.
- Error 403 on complete: inline on the card (above), form values kept.

## 4. Events

**Purpose:** send an event to runs that are waiting for it, the way an external system (an e-signature webhook, a booking confirmation) would.

**Route:** `/events`.

**Who sees what** (matrix below): the same readers as Workflow runs see the view; only the admin sees Send event and Export all.

**Data shown**
- **Waiting for events** (`GET /api/v1/waits`, infinite row model): FleetGrid with Event (mono, 160), Run (mono, links, 200), Workflow (200), Step (mono, 200), Correlation (mono, 240), Expires (relative, 180). Clicking a row fills the form below.
- **Send event form** (single column, max 560px): Event name (select from the waiting events, or typed), Correlation (key/value rows), Payload (mono JSON).

**Operations**

| Button | Variant | Permission | Result |
|---|---|---|---|
| Send event | default | `events.send` | `POST /api/v1/events/{name}`; toast "Event sent: 1 run woke". A 404 shows an inline coral alert "No run is waiting for `signed` with that correlation", form values kept. |
| Export CSV | outline (grid toolbar) | `waits.export` | AG Grid CSV of the loaded rows; toast "Export started" |
| Export all | outline (grid toolbar) | `waits.export` | `GET /api/v1/waits/export`; toast "Export started". Waits are Records: export only |
| Export (row menu) | menu item | `waits.export` | that wait's document, taken from the same export |

**Grid:** the waits list is FleetGrid. The form is grid-exempt.

**States**
- Loading: skeleton rows, then field skeletons.
- Empty (no waits): headline "No runs are waiting for an event". Text: "Runs wait for events at an event-wait step, such as a signature. Start a confirmation to see one." Action: "Open workflows" (secondary).
- Error: heading "Could not load waiting events". Cause, Retry, trace ID.

## 5. Incidents

**Purpose:** find runs that stopped because a step kept failing, and re-run the step once the cause is fixed.

**Route:** `/incidents`.

**Who sees what** (matrix below): only the admin (`incidents.read`) sees the view, Retry step and Export all.

**Data shown** (`GET /api/v1/incidents`, infinite row model):

| Column | Field | minWidth | Notes |
|---|---|---|---|
| Time | `at` | 180 | absolute local, UTC on hover; default sort descending |
| Run | `instanceId` | 200 | mono; links to view 2 |
| Workflow | `definition` | 200 | with version |
| Step | `step` | 200 | mono |
| Message | `message` | 320 | flex; mono; truncated, full text in the row detail |
| Status | `status` | 140 | Badge: Failed |

- **Row detail:** a dialog with the full message, the run, the step and the retry button.
- **Filters:** Workflow, Step (contains).

**Operations**

| Button | Variant | Permission | Result |
|---|---|---|---|
| Retry step | default (per row detail, one per view) | `incidents.retry` | `POST /api/v1/instances/{id}/retry`; toast "Step retried" |
| Export CSV | outline (toolbar) | `incidents.export` | CSV of loaded rows; toast "Export started" |
| Export all | outline (toolbar) | `incidents.export` | `GET /api/v1/incidents/export` with the filters; toast "Export started" |
| Export (row menu) | menu item | `incidents.export` | that incident's document, taken from the same export |
| Clear filters | ghost | `incidents.read` | resets the query string |

Incidents are an append-only history, so they are export-only (no import).

**Grid:** FleetGrid, infinite row model.

**States**
- Loading: nine skeleton rows.
- Empty: headline "No incidents". Text: "A run lands here when a step keeps failing after its retries. Nothing is stuck right now." No action.
- Error: heading "Could not load incidents". Cause, Retry, trace ID.

---

## Shell (all views)

- **Sidebar** (232px; a top bar with a menu button under 1024px): "Workflows" in Fraunces 20px as the app name ("Workflow engine"), then the nav: Workflows, Workflow runs, My tasks, Events, Incidents, Audit, Configuration. Each entry shows only when the acting person may read that view ("Who sees what").
- **Top bar right:** the "Acting as" persona switcher, the mode banner pill in mock mode ("Mock API: no backend, changes last until reload") and demo mode ("Demo data: changes reset on reload"). Live mode shows no banner. In demo mode the Demo button ("Run demo", secondary) replays the smoke and main-flow specs in this tab.
- **Toasts:** bottom-right, per STYLE.md §6.
- **Unknown route:** headline "This page does not exist", with a link to Workflows.

## Portable: export, import, promotion (pt.4, ui.5, ui.6)

PORTABLE.md section 3, applied. docs/RESOURCES.md says which resource is Config (export and import) and which is Records (export only).

- **Grids.** Every tabular data view is a FleetGrid with the infinite row model, so large sets page on the server (`offset`, `limit`, `total`, `sort`; the engine pages in the database): workflows, runs, incidents, waiting events, the audit log and the import preview. Exemptions, each with its reason: the run view and the workflow view (one record each, see their views); the **My tasks** inbox (cards that carry their own form and outcome buttons, see view 3; it asks for one server-side page, `limit` 100 of at most 500, never the whole set); the Send event form (a form, not a list); the Who sees what matrix (documentation). No `<table>` is rendered anywhere in the studio.
- **Toolbar.** Export CSV (the loaded rows), then **Export all** (the documents, in the fleet envelope: an array of `{kind, version, key, …}`), then **Import** on Config grids only (workflows). Each shows only when `can()` says so.
- **Row menu.** The "⋯" at the end of each row opens a menu with **Export** (that row's document). The row click still opens the row; the menu does not.
- **Import flow** (a 480px dialog from a grid; inline on the Configuration screen): choose or drop a file (JSON, at most 5 MB) → the engine's preview (`POST …/import/preview`, writes nothing) → one grid per kind with **New**, **Overwrite** (and the fields that change), **Unchanged**, **Conflict** or **Missing ref** → **Confirm import** (`POST …/import`, all or nothing) → toast "Imported: 3 created, 1 updated" (plus "N unchanged" and "N conflicts skipped" when there are some). A missing ref disables Confirm and says why. A file the engine refuses (400) lists where it is wrong, as JSON pointers, and nothing was written.
- **Configuration** (`/configuration`, nav entry "Configuration", shown when the person may export or import): the promotion screen. **Export configuration** downloads the bundle (`GET /api/v1/export`, every Config kind in dependency order, environment values as `${VAR}` placeholders). **Import configuration** is the import flow on the bundle (`POST /api/v1/import/preview`, `/import`), the preview grouped by kind.
- **Gating.** Export and Import render only when `can('<resource>.export')` or `can('<resource>.import')` is true (the engine's dotted names; the fleet text writes them with a colon). `cypress/e2e/features/portable.cy.ts` signs in as `w.support` (the workflow-support role, no export or import) and sees none of the buttons, no row menu and no Configuration entry.
- **Mocks.** The MSW mock implements the same routes from one TypeScript codec (`src/mocks/portable.ts`): the envelope, the plan (create, overwrite, unchanged, conflict, missing-ref) and the all-or-nothing import. `contract.test.ts` checks every response against `contract/openapi.yaml`. In the mock every persona gets the permissions of its roles from `GET /me`.
- **Tests.** One round trip through the toolbar (export, import unchanged, import with a new workflow, export again deep-equal, overwrite naming its field), a bad file that writes nothing, a bundle whose missing ref blocks Confirm, the bundle export, and the gating test. They run in the mock profile (`pnpm cy:run`); the demo profile runs the same app build (`make web-demo-check`).

## 6. Audit

**Purpose:** read who changed what, and every request that was refused. Read only: there is no way to change or delete a row.

**Route:** `/audit` (sidebar entry shown with `audit.read`). **Data shown** (`GET /api/v1/audit`, infinite row model, newest first): Time, Actor, Via, Action, Resource, Outcome, and the names (never the values) of the fields that changed. **Filters:** Actor, Action, Resource (contains, mono). **Operations:** Export all (`audit.export`, toolbar), Export CSV (`audit.export`), Clear filters. **Grid:** FleetGrid, infinite row model. **States:** loading skeleton rows; empty "Nothing recorded yet" (or "No entries match these filters" with Clear filters); error "Could not load the audit log" with Retry and the trace ID.

## 7. Configuration

**Purpose:** move the configuration (workflows) to another environment.

**Route:** `/configuration` (sidebar entry shown with `workflows.export` or `workflows.import`). **Data and operations:** the Export configuration card (`workflows.export`, `GET /api/v1/export`) and the Import configuration card (`workflows.import`, the preview-then-confirm flow described under Portable). **Grid:** the import preview grids are FleetGrids. **States:** the import flow's own (drop zone, preview, blocked by a missing reference, error with the engine's problem+json).

## Who sees what

One matrix for every view above: a role sees an action when its permissions (from `GET /api/v1/me`) allow it, so a blank cell means the action is **hidden** (a whole view with no read permission is hidden from the sidebar, and its address shows the 403 reason). The role to permission map is the engine's persona file; the personas are `a.ops`, `l.jones`, `ops.admin`, `i.handler`, `w.support`, `r.viewer`, `e.admin` and `p.maint` (no role). This table is the source of truth for the persona specs: `cypress/e2e/rbac/<persona>.cy.ts` read it and assert that exactly these actions are on screen for that persona (ui.3, ui.4, ui.6). `task-outcome-*` is the outcome buttons on a task card.

| Screen | Action | `data-cy` | requester `a.ops` | legal `l.jones` | ops `ops.admin` | incident handler `i.handler` | support `w.support` | auditor `r.viewer` | admin `e.admin` | no role `p.maint` |
|---|---|---|---|---|---|---|---|---|---|---|
| Sidebar | Workflows | `nav-workflows` | ✓ | | | | ✓ | ✓ | ✓ | |
| Sidebar | Workflow runs | `nav-instances` | ✓ | | | ✓ | ✓ | ✓ | ✓ | |
| Sidebar | My tasks | `nav-tasks` | ✓ | ✓ | ✓ | | | ✓ | ✓ | |
| Sidebar | Events | `nav-events` | ✓ | | | ✓ | ✓ | ✓ | ✓ | |
| Sidebar | Incidents | `nav-incidents` | | | | | | | ✓ | |
| Sidebar | Audit | `nav-audit` | | | | | | ✓ | ✓ | |
| Sidebar | Configuration | `nav-configuration` | | | | | | | ✓ | |
| 1 Workflows | Export all | `workflows-export-all` | | | | | | | ✓ | |
| 1 Workflows | Import | `workflows-import` | | | | | | | ✓ | |
| 1 Workflow | Start run | `workflow-start` | ✓ | | | | | | ✓ | |
| 1 Workflow | Download SVG | `workflow-download-svg` | | | | | | | ✓ | |
| 2 Workflow runs | Export all | `instances-export-all` | | | | | | | ✓ | |
| 2 Workflow run | Retry step | `run-retry` | | | | ✓ | | | ✓ | |
| 2 Workflow run | Restart at step | `run-restart` | | | | | ✓ | | ✓ | |
| 2 Workflow run | Fire timeout | `run-fire-timeout` | | | | | | | ✓ | |
| 3 My tasks | Everyone's tasks | `tasks-everyone` | ✓ | ✓ | ✓ | | | ✓ | ✓ | |
| 3 My tasks | Outcome buttons | `task-outcome-*` | ✓ | ✓ | | | | | ✓ | |
| 3 My tasks | Reassign task | `task-reassign` | | | ✓ | | | | ✓ | |
| 4 Events | Send event | `event-send` | | | | | | | ✓ | |
| 4 Events | Export all | `waits-export-all` | | | | | | | ✓ | |
| 5 Incidents | Retry step (row detail) | `incident-retry` | | | | | | | ✓ | |
| 5 Incidents | Export all | `incidents-export-all` | | | | | | | ✓ | |
| 6 Audit | Export all | `audit-export-all` | | | | | | | ✓ | |
| 7 Configuration | Export configuration | `config-export` | | | | | | | ✓ | |
| 7 Configuration | Import configuration | `import-choose` | | | | | | | ✓ | |

Reading it: the requester (`requester` role) starts runs and reads workflows and runs; legal (and the other approver roles) works the task inbox; ops reassigns; the incident handler retries a failed run; workflow support restarts a run at a step; the auditor reads everything and exports nothing; the admin holds every permission; a person with no role sees no views, only the reason. A person who may complete a task but does not hold its role still sees the buttons and gets the engine's 403 reason on the card (that is the Demo's "blocked" chapter).

## Demo chapters

The Demo is the studio's mocked build (`make web-demo`, `make web-demo-build`, `make share`): real Cypress specs replayed in the viewer's own tab. Chapters, in play order (`apps/web/src/demo/chapters.ts`); the last column says which of the earlier no-build UI's chapters each one carries on.

| # | Chapter | Spec | Earlier chapter |
|---|---|---|---|
| 1 | Browse a workflow and its diagram | `smoke/workflow.cy.ts` | browse |
| 2 | Start a run with the sample variables | `features/start-run.cy.ts` | timeline |
| 3 | Support restarts a run at another task | `features/restart.cy.ts` | operations (restart) |
| 4 | A person without the role is blocked, then the right person approves | `features/complete-task.cy.ts` | tasks (blocked) |
| 5 | Reject, resubmit and approve | `features/complete-task.cy.ts` | tasks (loop) |
| 6 | Ops moves a task to another person | `features/reassign.cy.ts` | operations (reassign) |
| 7 | A correlated event completes the run | `features/send-event.cy.ts` | events (event) |
| 8 | A timeout takes the TIMED_OUT path | `features/timeout.cy.ts` | events (timeout) |
| 9 | An incident is retried | `features/retry.cy.ts` | incidents |
| 10 | Read who did what in the audit log | `smoke/audit.cy.ts` | (new) |

Keys (fleet standard): Space Space pause or play, Shift up or down speed, Shift right right skip a step, Tab Tab chapters menu, Esc close the menu or leave. Each has an on-screen button.

## Layout (v1.5)

The studio (`apps/web`) uses the 12-column page grid with no max-width on the content (caps only: forms 560px, prose 72ch, dialogs 480px). The shell is the viewport (`100dvh`); the sidebar and the content scroll on their own. Under 1024px every region is span 12. In the studio the primitive is `PageGrid` (`src/components/PageGrid.tsx`; `fill` and `above` make the last row take the remaining height). The 1920px step (`3xl`) widens the Detail main column from 8 to 9 spans.

| page | archetype | regions |
|---|---|---|
| Studio: Workflows, Workflow runs, Incidents | List | header, filters (and key figures on runs), then the FleetGrid fills the remaining height (min 320px) |
| Studio: Workflow, Workflow run | Detail | main span 8 (diagram and steps, or where it is and the timeline), side span 4 (sample variables, or variables) |
| Studio: Events | Master-detail | the waits grid span 7 (8 at 1920px) filling the height, the Send event form span 5 (4) beside it |
| Studio: My tasks | Overview | task cards as tiles in `repeat(auto-fit, minmax(420px, 1fr))` |

Key figures use `repeat(auto-fit, minmax(200px, 1fr))` (STYLE suggests 280px; four tiles must fit one row at 1440px). Proved by a Cypress `layout` spec at 1920×1080, 1440×900 and 390×844: `apps/web/cypress/e2e/layout` (part of the mocked fast gate).
