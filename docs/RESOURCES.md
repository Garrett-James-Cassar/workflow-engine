# Resources: what is portable

How this app applies fleet `PORTABLE.md`. Every resource has a class; the class decides what the API offers. The table below is read by `PortableFitnessTest`: each row names the controller that must expose an export route (and an import route, for Config).

| Resource | Class | Controller | Export | Import | Key |
|---|---|---|---|---|---|
| workflows | Config | WorkflowController | `GET /api/v1/workflows/export`, `GET /api/v1/workflows/{key}/export` | `POST /api/v1/workflows/import/preview`, `POST /api/v1/workflows/import` | `<name>:<version>` |
| instances | Records | InstanceController | `GET /api/v1/instances/export`, `GET /api/v1/instances/{id}/export` | none | instance id |
| tasks | Records | TaskController | `GET /api/v1/tasks/export` | none | task id |
| waits | Records | WaitController | `GET /api/v1/waits/export` | none | wait id |
| incidents | Records | IncidentController | `GET /api/v1/incidents/export` | none | instance id |
| events | Records | EventController | `GET /api/v1/events/export` | none | event id |
| audit | Records | AuditController | `GET /api/v1/audit/export` | none | audit row id |
| people | Records | RbacController | `GET /api/v1/people/export` | none | login |

**Bookkeeping, not resources:** `config_docs` holds the imported Config documents (they are the `workflows` resource, with no route of its own); `audit_log` is the `audit` resource above (it replaced `import_audit`; each import is one `workflows.import` row).

**Secrets and environment bindings:** none. The app stores no credential, token or key; the only secrets it reads are its own settings (`DB_PASSWORD`), which are not resources and have no route.

**Promotion bundle:** `GET /api/v1/export` writes every Config kind in dependency order; `POST /api/v1/import/preview` and `POST /api/v1/import` take it back (or any single document or array of documents of any Config kind). With one Config kind today, the bundle is the `workflows` documents.

## Envelope

```json
{ "kind": "workflow", "version": 1, "key": "trade-booking:1", "name": "trade-booking", "workflowVersion": 1, "sample": {}, "steps": [] }
```

A file holds one envelope or an array of them. `version` is the envelope version (this app writes and reads 1; a newer one is a 400). `toDoc` is deterministic: keys are sorted, storage bookkeeping (row ids, creation times) is left out, so export, import, export is byte-identical.

A workflow's own version is `workflowVersion` and is part of the key, because a version never changes once runs use it (CLAUDE.md: behaviour change = version bump).

## Import rules

- Match on `key`. Same content: `unchanged`. A new key: `create`. Different content: `overwrite`.
- A workflow defined in code (the DSL objects in `app/workflow/`) cannot be changed by an import: the same key with different content is `conflict`, counted and skipped. Only documents that arrived through an import can be overwritten.
- The whole file is validated before anything is written. A bad document is a 400 `problem+json` with `errors: [{path: "/3/steps/0/id", message}]`, and nothing is written. Writes happen in one transaction, and each import adds one `workflows.import` row to `audit_log` with the counts.
- `POST .../import/preview` returns `{entries: [{kind, key, action, changed, missing}]}` and writes nothing (not even the audit row). Actions: `create`, `overwrite`, `unchanged`, `conflict`, `missing-ref`.
- Both import routes use the heavy rate limit and a 5 MB body cap (`MAX_IMPORT_BYTES`); everything else keeps `MAX_BODY_BYTES` (1 MB).

## References

References are by key and never by internal id. A workflow step that runs code names its handler by class name (`"handler": "dev.workflow.app.samples.BookTrade"`). The importing app must have that class: if not, the preview shows `missing-ref` (with the pointer to the step) and the import is refused with a 400, writing nothing.

Roles named by a user task (`assignee.role`) are resolved by RBAC when the task is created, not at import, because the directory may be remote.

A decision's code (`evaluate { }`) is not data. It is written as `"evaluator": "code"`, and an imported decision stops the run if it is ever reached. Imported workflows are stored and exported; running them needs the DSL text import (not built yet).

## Placeholders and credentials

An HTTP step's header that is not filled from run vars (`{doc.id}`) is a literal and might be a credential. It is exported as a `${STEP_HEADER}` placeholder, never as itself; the importing environment supplies the value. Hosts come from run vars (`{services.screening}`), so a document holds no host. `sample` holds example data for previews and tests; it is not environment configuration. The app has no stored credentials, so there is no `credentialRef`.

## Permissions

Named `<resource>.<verb>` as in `RBAC.md` (the fleet text writes `<resource>:export`; this app keeps its dotted names). In the persona file and `Permissions.kt`: `workflows.export`, `workflows.import`, `instances.export`, `tasks.export`, `waits.export`, `incidents.export`, `events.export`, `people.export`. The `engine-admin` role holds all of them. The bundle routes need `workflows.export` and `workflows.import` (the one Config kind).

## Records limits

Records exports cover the newest 10,000 runs and their open tasks, open waits and events. `tasks` are the open tasks the caller may read (closed ones are in `events`); `incidents` are runs in the failed state. There is no import: a Records kind is history.
