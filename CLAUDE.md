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
- **Mermaid:** loaded from the jsdelivr CDN, pinned to 11.4.1. Diagrams render on a light `--paper` panel so the exporter's node fills read the same as on GitHub.
