# Working in workflow-engine

Kotlin 25/Spring Boot 4.1 + Gradle; React studio in `apps/web` (pnpm). Routes: `/docs`, `/openapi.json` (not copied anywhere else).

## Invariants
- `engine/core` is pure Kotlin (no Spring, no I/O); `Engine` is a pure transition function, the app applies its effects.
- Every step yields an enum outcome; route with `on { when (it) { … } }`. Never add `else` branches to sample workflows.
- New workflow: DSL `object : Workflow(...)` in `engine/app/.../app/workflow/`, list it in `adapters/Workflows.kt`, handlers in `app/samples`, then `./gradlew diagrams` (DiagramsUpToDateTest). Behaviour change = version bump.
- New resource or route: classify it in `docs/RESOURCES.md` (Config = export+import, Records = export only); its controller needs the `…/export` mapping or `PortableFitnessTest` fails.
- Tests: core kotlin.test; compile-fail cases in `CompileFailTest`; app e2e on H2 + `MutableClock` (`ApiTest` base, one shared context); Postgres in `postgresTest`. Name tests with the milestone (`E5 …`).

## Ports, env, gate tiers
- Engine **47831** (`PORT`), Postgres **55434** (`DATABASE_URL`, `DB_USER`, `DB_PASSWORD`), studio **5183**, demo build **15183**, rbac 47861 (`RBAC_URL`, optional). Also `AUTH_MODE`, `RATE_LIMIT_{ENABLED,READ_PER_MIN,WRITE_PER_MIN,HEAVY_PER_MIN}`, `MAX_BODY_BYTES`, `MAX_IMPORT_BYTES` (5 MB, import routes), `CORS_ORIGINS` (default: studio 5183/15183), `TLS`, `WORKFLOW_HTTP_ALLOWED_HOSTS`, `TIMEOUT_POLL_INTERVAL`, `OTEL_EXPORTER_OTLP_ENDPOINT`. `make dev` runs it; `make stop` stops it.
- `make fast` (pre-commit; lint, typecheck, ArchUnit, detekt, H2 tests; no browser) · `make push-gate` (+ mocked smoke) · `make ci` (everything; once at the end of a task).
- Standards, one line each: [trunk-based, fast feedback](/Users/garrett.cassar/Projects/fleet/standards/FEEDBACK.md) · [arch rules](/Users/garrett.cassar/Projects/fleet/standards/ARCHITECTURE-RULES.md) (map: `fleet-layers.json`, `ARCHITECTURE.md`) · [security](/Users/garrett.cassar/Projects/fleet/standards/SECURITY.md) · [UI style](/Users/garrett.cassar/Projects/fleet/standards/ui/STYLE.md).
- Deploy, rollback, incidents: [RUNBOOK.md](RUNBOOK.md). Commits: Conventional Commits (`type(scope): subject`, checked by the `commit-msg` hook), summarised in [CHANGELOG.md](CHANGELOG.md) under `[Unreleased]`.
- How this app applies them (API versioning, input limits, observability, console Demo, studio decisions): [docs/conventions.md](docs/conventions.md).

## Where things live (`engine/app/src/main/kotlin/dev/workflow/app` = `app/`)
| route / screen / concept | files |
|---|---|
| `/health`, `/ready` (DB probe), `/definitions*` (list, graph, export) | `app/api/DefinitionController.kt` · `runtime/ReadinessService.kt`, `persistence/JdbcDatabaseProbe.kt` · `mappers/Views.kt` (`DefinitionMapper`, `ExportMapper`) · `runtime/Queries.kt` (`DefinitionService`) · `adapters/WorkflowRegistry.kt` |
| `/definitions/{n}/instances`, `/instances*`, retry, restart | `api/InstanceController.kt` · `runtime/InstanceService.kt`, `Transitions.kt` · `persistence/InstanceRepository.kt` · DTOs `api/dtos/Dtos.kt` |
| `/tasks*` (list, complete, reassign) | `api/TaskController.kt` · `runtime/TaskService.kt`, `FormValidation.kt` · `persistence/TaskRepository.kt` · `security/TaskAuthorizer.kt` |
| `/events/{name}`, `/timeouts/…` | `api/EventController.kt` · `InstanceService.deliverEvent/fireTimeout` · `runtime/TimeoutPoller.kt` · `persistence/WaitRepository.kt` |
| `/me`, `/rbac/people`, permissions | `api/AccessController.kt` · `runtime/Queries.kt` (`AccessService`) · `security/Identity.kt` (`Authorizer`), `Permissions.kt` · `adapters/LocalRbac.kt`, `clients/HttpRbac.kt` |
| `/demo/*`, console reset | `api/DemoController.kt`, `DemoProviders.kt` · `runtime/DemoGate.kt` · `samples/` |
| errors, validation, 413/415, rate limit | `api/ErrorHandler.kt` · `config/web/ValidationProblems.kt`, `BodyGuardFilter.kt`, `RateLimitInterceptor.kt`, `ResponseValidation.kt`, `CorsFilter.kt` · `config/SecurityHeadersFilter.kt` |
| `/api/v1` prefix, deprecated aliases | `config/ApiVersionConfig.kt`, `config/web/DeprecatedAliasFilter.kt` |
| identity, labels, access log | `config/web/IdentityInterceptor.kt`, `EndpointLabelInterceptor.kt`, `AccessLogFilter.kt` · `telemetry/` |
| export/import (portable), bundle `/export` `/import` | [docs/RESOURCES.md](docs/RESOURCES.md) (class of each resource; `PortableFitnessTest` reads it) · `app/portable/` (`Codec.kt`, `WorkflowCodec.kt`, `ImportPlanner.kt`, `ConfigPortability.kt`, `RecordExports.kt`) · `api/PortableController.kt`, `api/RecordsController.kt` + `…/export` routes in the resource controllers |
| DSL, validation, graph, Mermaid, `Engine` | `engine/core/src/main/kotlin/dev/workflow/core/` (`Dsl.kt`, `StepBuilders.kt`, `DefinitionValidator.kt`, `Engine.kt`) |
| sample workflows | `app/workflow/*.kt` · handlers `app/samples/` · diagrams `docs/workflows/` |
| console (no build) | `engine/app/src/main/resources/static/app.js`; Demo specs `engine/console-e2e/cypress/e2e/chapters`, player `static/console-demo/` (committed; `make console-build`) |
| studio screens (workflows, runs, tasks, events, incidents) | `apps/web/src/routes/<name>/` · port `src/api/port.ts` · mock `src/mocks/routes-*.ts` · contract `apps/web/contract/openapi.yaml` · specs `apps/web/cypress/e2e/` · spec text `UI-SPEC.md` |

## Gotchas
- macOS `sed -i` is BSD: use Edit or Python. Use absolute paths; the shell cwd resets.
- Build the code before trusting detekt: it parses but does not compile. Run `./gradlew :engine:app:test` after a refactor.
- `ApiTest` sets `workflow.rate-limit.enabled=false`; `RateLimitTest` is the one test with the limiter on. A new test needing its own context costs 5–10 s.
- Cypress next to a busy Gradle times out (10 s): `make ci` runs it after the parallel part. Specs pass alone.
- The Gradle daemon has a short PATH: `npx` (jscpd) is found via `sh -c` in `engine/app/build.gradle.kts`.
- API change: run `make openapi` and commit `openapi.json` (a test fails when it is stale). `make api-breaking` (oasdiff via Docker; in `ci` and `push-gate`, not `fast`) diffs it against `origin/main`. A break needs `/api/v2`; `API_BREAKING_OK=1` passes only together with a CHANGELOG.md entry.
- Rbac persona file is read leniently; Jackson is strict everywhere else (unknown fields → 400).

## Do not touch
- Detekt limits (`config/detekt/*.yml`), ArchUnit rules, `.jscpd.json` threshold: never raise or suppress; fix the code.
- `static/console-demo/` and `static/vendor/` by hand (built or vendored); `apps/web/src/api/generated/` (run `gen:api`); `apps/web/src/demo/vendor`.
- Never push; only the coordinator's `/fleet-push`.
