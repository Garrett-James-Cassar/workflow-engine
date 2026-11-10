.PHONY: backup restore restore-drill seed reseed openapi api-breaking web-static web-browser fast fast-be fast-web push-gate gradle-check up down stop dev demo restart test ci check-placement diagrams hooks web web-demo web-install web-gen-check web-typecheck web-lint web-test web-build web-demo-build web-cy web-smoke web-demo-check web-live-check web-ci share share-studio share-studio-local cy-live console-install console-build console-bundle-check console-typecheck console-cy console-smoke console-demo-check console-ci

PORT ?= 47831
export PORT
OPEN_PATH ?= /

up: ## start Postgres (127.0.0.1:55434)
	docker compose up -d --wait db

down:
	docker compose down

# Stop a running copy of this engine on $(PORT). Refuses to touch any other process on that port.
stop:
	@pids=$$(lsof -ti tcp:$(PORT) -sTCP:LISTEN 2>/dev/null); \
	if [ -z "$$pids" ]; then echo "nothing listening on $(PORT)"; exit 0; fi; \
	for pid in $$pids; do \
	  if ps -o command= -p $$pid | grep -q 'dev\.workflow\.app'; then \
	    kill $$pid && echo "stopped workflow engine (pid $$pid) on $(PORT)"; \
	  else \
	    echo "port $(PORT) is used by pid $$pid, which is not the workflow engine; not touching it"; exit 1; \
	  fi; \
	done; \
	for i in 1 2 3 4 5 6 7 8 9 10; do \
	  lsof -ti tcp:$(PORT) -sTCP:LISTEN >/dev/null 2>&1 || exit 0; sleep 1; \
	done; \
	echo "pid still holding $(PORT) after 10s; stop it by hand"; exit 1

# Always a fresh start: stop any running engine, make sure Postgres is up, then run the engine.
# Once the engine answers /health, open the console in the default browser (macOS `open`).
# Set NO_BROWSER=1 to skip it. Gives up after two minutes so it never hangs in the background.
dev: stop up ## run the engine on $(PORT) against compose Postgres (Flyway migrates on start)
	@if [ -z "$(NO_BROWSER)" ]; then \
	  ( for i in $$(seq 1 120); do \
	      curl -sf http://localhost:$(PORT)/health >/dev/null 2>&1 && { open "http://localhost:$(PORT)$(OPEN_PATH)"; exit 0; }; \
	      sleep 1; \
	    done ) & \
	fi; \
	ALLOW_RESET=$${ALLOW_RESET:-true} ./gradlew :engine:app:bootRun

restart: dev

demo: ## the engine with the console Demo: opens http://localhost:$(PORT)/?demo and starts the player (dev auth only, never live)
	$(MAKE) dev OPEN_PATH='/?demo'


# ---- data (dm.3, dm.5): backups of the compose Postgres, and demo data from the Object Mother ----
backup: ## dm.3: timestamped pg_dump of the compose Postgres into ./backups/ (gitignored)
	@scripts/db.sh backup

restore: ## dm.3: restore a dump over the compose Postgres: make restore FILE=backups/workflow-<stamp>.sql
	@scripts/db.sh restore "$(FILE)"

restore-drill: ## dm.3: backup, wipe, restore, compare row counts, report the restore time
	@scripts/db.sh drill

seed: ## dm.5: start the Object Mother's sample runs in the running engine on $(PORT); idempotent (does nothing when runs exist)
	./gradlew :engine:app:seed

reseed: ## dm.5: wipe every run (the engine needs ALLOW_RESET=true) and seed again
	./gradlew :engine:app:seed --args=reset

test: ## unit + compile-fail + e2e (Testcontainers; needs Docker)
	./gradlew check

hooks: ## install the git hooks (lefthook: pre-commit make fast + secrets, pre-push make push-gate)
	lefthook install

check-placement: ## arch.8: every class sits in a role package (ArchUnit everyClassHasARole, with the layering rules); also part of `./gradlew check`
	./gradlew :engine:app:test --tests 'dev.workflow.app.architecture.ArchitectureTest'

# ---- gate tiers (fleet FEEDBACK.md): fast <= 30 s warm, push-gate <= 2 min, ci <= 5 min ----
fast-be:
	./gradlew --quiet :engine:app:test :engine:core:test detektEdge detektCore

fast-web:
	$(WEB) lint && $(WEB) typecheck && $(WEB) test

fast: ## lint, typecheck, fitness (ArchUnit + detekt size limits), unit + backend e2e on H2; no browser, no Docker
	@$(MAKE) -j2 fast-be fast-web

push-gate: fast api-breaking ## fast + the API breaking-change check + the mocked Cypress smoke subset (lefthook pre-push)
	$(WEB) cy:smoke

gradle-check:
	./gradlew check

# Gradle runs beside the web's non-browser steps; the Cypress steps follow alone, because a browser timing out
# next to a busy Gradle is flake, not signal.
ci: ## everything: Gradle check || web static steps, then the web browser steps
	@$(MAKE) -j2 gradle-check web-static
	@$(MAKE) api-breaking
	@$(MAKE) web-browser

# ---- API contract (api.5): the engine's served OpenAPI is committed as openapi.json ----
SPEC := openapi.json
API_BASELINE ?=
API_CURRENT ?= $(SPEC)
OASDIFF := docker run --rm -v "$(CURDIR):/w" -w /w tufin/oasdiff:v1.11.7

openapi: ## rewrite the committed openapi.json from the served spec (OpenApiSpecTest fails when it is stale)
	OPENAPI_WRITE=1 ./gradlew --quiet :engine:app:cleanTest :engine:app:test --tests 'dev.workflow.app.OpenApiSpecTest'

# Breaking changes need /api/v2 (API.md). Baseline: the spec on origin/main (or API_BASELINE=<file>, for trials).
# API_BREAKING_OK=1 lets an intentional break through, only together with a CHANGELOG.md change against origin/main.
api-breaking: ## oasdiff breaking: origin/main's openapi.json vs the working copy; fails on ERR
	@mkdir -p build; \
	base="$(API_BASELINE)"; \
	if [ -z "$$base" ]; then \
	  if ! git cat-file -e origin/main:$(SPEC) 2>/dev/null; then echo "api-breaking: origin/main has no $(SPEC) yet; skipped"; exit 0; fi; \
	  git show origin/main:$(SPEC) > build/openapi-baseline.json; base=build/openapi-baseline.json; \
	fi; \
	if $(OASDIFF) breaking "$$base" "$(API_CURRENT)" --fail-on ERR; then echo "api-breaking: no breaking changes"; exit 0; fi; \
	if [ "$(API_BREAKING_OK)" = "1" ] && ! git diff --quiet origin/main -- CHANGELOG.md; then echo "api-breaking: allowed by API_BREAKING_OK=1 with a CHANGELOG.md entry"; exit 0; fi; \
	echo "api-breaking: breaking change. Add /api/v2, or set API_BREAKING_OK=1 together with a CHANGELOG.md entry"; exit 1

diagrams: ## regenerate docs/workflows from the DSL
	./gradlew :engine:app:diagrams

# ---- web studio (apps/web, see docs/ui-plan.md) ----
WEB := pnpm -C apps/web

web-install: ## install the web studio's dependencies
	$(WEB) install --frozen-lockfile

web: ## studio on :5183, mock profile (MSW + Object Mother; ?scenario=empty|error|slow)
	$(WEB) dev

web-demo: ## studio on :5183, demo profile (mock reset per load + Run demo button)
	$(WEB) dev:demo

web-gen-check: ## the generated client is up to date with contract/openapi.yaml
	$(WEB) gen:api && git diff --exit-code -- apps/web/src/api/generated/schema.d.ts

web-typecheck:
	$(WEB) typecheck

web-lint: ## STYLE.md section 10 grep gate (tokens only, no caps, no native date inputs)
	$(WEB) lint

web-test: ## vitest, including the contract test (every mock response validated against openapi.yaml)
	$(WEB) test

web-build:
	$(WEB) build

web-demo-build: ## static demo build for the fleet stack: apps/web/dist (index.html + mockServiceWorker.js)
	$(WEB) build:demo

web-cy: ## all Cypress specs, headless, mocked, with the 80% coverage gate
	$(WEB) cy:run

web-smoke: ## Cypress smoke specs only (the lefthook pre-push hook)
	$(WEB) cy:smoke

web-demo-check: ## plays every Demo chapter unpaced in the demo build (demo.7)
	$(WEB) cy:demo

web-live-check: ## the live profile with nothing serving the contract shows the error state, no mode banner
	$(WEB) cy:unreachable

web-static: web-gen-check web-typecheck web-lint web-test web-build

web-browser: web-cy web-demo-check web-live-check

web-ci: web-static web-browser

cy-live: ## Cypress against the real engine on :47831 and Postgres on 55434 (opt-in; nightly in e2e-live.yml)
	$(WEB) cy:live

share-studio: ## serve the studio DEMO build through ngrok (needs NGROK_AUTHTOKEN); remote viewers run its Demo in their own tab
	$(WEB) exec node scripts/share.mjs

share-studio-local: ## serve the studio demo build on 0.0.0.0:5183 without a tunnel
	$(WEB) exec node scripts/share.mjs --no-tunnel

# ---- console Demo (engine/console-e2e): the console's Cypress specs, which are also the Demo chapters ----
CONSOLE := pnpm -C engine/console-e2e

console-install: ## install the console e2e package's dependencies
	$(CONSOLE) install --frozen-lockfile

console-build: ## rebuild the Demo player into engine/app/src/main/resources/static/console-demo
	$(CONSOLE) build:demo

console-bundle-check: console-build ## the committed player bundle is up to date with its sources
	git diff --exit-code -- engine/app/src/main/resources/static/console-demo

console-typecheck:
	$(CONSOLE) typecheck

console-cy: ## chapters, smoke (axe) and layout specs against the RUNNING engine on $(PORT) (make dev)
	$(CONSOLE) cy:run

console-smoke: ## the console smoke specs only (needs the engine up)
	$(CONSOLE) cy:smoke

console-demo-check: ## plays every Demo chapter unpaced in the in-page runtime against the running engine (demo.7)
	$(CONSOLE) cy:demo

console-ci: console-bundle-check console-typecheck console-cy console-demo-check ## the console gate; the engine must be running (CI starts it)

share: ## tunnel the console (the engine on $(PORT)) through ngrok so a remote viewer runs the Demo in their own tab
	$(CONSOLE) exec node scripts/share.mjs --port $(PORT)
