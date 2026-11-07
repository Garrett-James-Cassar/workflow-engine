# JVM architecture preset (Kotlin / Spring): ArchUnit + detekt

This is the JVM version of `../dep-cruiser.base.cjs` and `../eslint.arch.mjs`. The rules are in `../../ARCHITECTURE-RULES.md` and are identical for every stack. Only the tools differ.

## Layer and dependency rules: ArchUnit
- **Version:** `com.tngtech.archunit:archunit-junit5:1.5.1` (September 2026; Java 25 bytecode).
- **Setup:** copy `ArchitectureTest.kt` into `src/test/kotlin/<base package>/architecture/` and fill in `ROLES` from your `fleet-layers.json`, which for JVM apps maps **packages** (for example `dev.workflow.app.api`).
- **Gate:** it runs as an ordinary JUnit test, so it's in the fast gate.

## Size and complexity: detekt
- **Version:** `io.gitlab.arturbosch.detekt` 1.23.8 is the last stable release but is built for Kotlin 2.0. Apps on Kotlin 2.4 need the **2.x line**: plugin id `dev.detekt`, latest `2.0.0-alpha.6` (August 2026).
- **Proving it works:** the first adopter must show that detekt runs on its code (no parse or compiler-version errors) and report the version used. If no detekt version can analyse Kotlin 2.4, stop and report. Don't silently skip complexity.
- **Per-role limits:** detekt rules have one threshold per config, so use **two detekt tasks**, each with its own config and source filter:
  - `detektEdge`: controllers, clients, adapters, repositories, mappers packages
  - `detektCore`: services and domain packages

  Both run in `make ci`.
- **Option names:** take them from **your detekt version's** generated default config (`detektGenerateConfig`). Names changed between 1.x and 2.x, so don't copy 1.x names blindly.

| limit | edge (controllers, clients, adapters, repositories, mappers) | core (services, domain) |
|---|---|---|
| `CyclomaticComplexMethod` (counts `if`, `when` branches, loops, `&&`/`\|\|`, `?:`; set `ignoreSimpleWhenEntries` false) | threshold **3** | **10** |
| `CognitiveComplexMethod` | n/a | **15** |
| `LongMethod` (detekt counts lines, not statements) | **6 lines** (≈ 4 statements plus the signature and close) | **20** |
| `NestedBlockDepth` | **1** | **3** |
| `LongParameterList` (functions) | **3** | **4** |
| `LargeClass` / file length | 200 lines | 200 lines |

- **DTOs** (data classes and schemas) should have no logic. Exclude them from both tasks, and enforce "no functions beyond generated ones" with an ArchUnit rule if needed.
- **Duplication (arch.9):** run `jscpd` (`../jscpd.json`, format `kotlin`) or detekt's duplication rules.
- **Placement (arch.8):** the ArchUnit `everyClassHasARole` test below.

**Honest note:** detekt measures **lines**, not statements. The JVM edge limit is therefore "≤ 6 lines per function body". That's the closest equivalent of TypeScript's `max-statements: 4`, and it's recorded here as the JVM form of the same rule, not a weaker one.
