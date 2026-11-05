# workflow-engine — Milestones
## In progress
- (none) v1 (E1–E6) built 2026-11-04; awaiting review and iteration
## Done
- E1: Gradle (Kotlin 2.4, Spring Boot 4.1, Java 25) scaffold, Postgres + Flyway, /health, dev.json, make ci (2026-11-04)
- E2: DSL: outcome-typed steps, exhaustive `on { when }`, next/goto/end/path with rejoin, startup validation; compile-fail tests (2026-11-04)
- E3: WorkflowGraph, MermaidExporter, `./gradlew diagrams`, stale-diagram test (2026-11-04)
- E4: pure engine; service tasks (handler beans + built-in http with host allowlist), retries → incident → retry; decisions (2026-11-04)
- E5: user tasks with outcomes and forms; role/user/userFrom assignment; RBAC stub from config (2026-11-04)
- E6: event waits with correlation; timeouts as the TIMED_OUT outcome via an in-process poller + /timeouts callback (2026-11-04)
## Next
- v2: sub-workflows: `subWorkflow("id", Other)` step whose outcome is the child's end status
- E7: real rbac service client, JWT in live, export/import (PORTABLE)
- E8: preview API; S1–S8 workflow studio
- E9: StepKind SPI with shape descriptors
- Scheduler adapter for timeouts (currently in-process poller); mermaid-cli render in CI; fleet toolchain + compose entries
## Context
Simple Kotlin-DSL workflow engine for the SmartDX replacement: user tasks (RBAC-assigned approvals), service tasks (linked Kotlin code or HTTP), event waits (receive endpoints), decisions. Every step yields an outcome enum; `on { when (it) { … } }` routes it, and the compiler rejects unrouted outcomes. Workflows draw themselves as Mermaid. Plan: ~/.claude/plans/alright-chap-i-want-replicated-nebula.md
