.PHONY: up down stop dev demo restart test ci check-placement diagrams hooks web web-demo web-install web-gen-check web-typecheck web-lint web-test web-build web-demo-build web-cy web-smoke web-demo-check web-live-check web-ci share share-studio share-studio-local cy-live console-install console-build console-bundle-check console-typecheck console-cy console-smoke console-demo-check console-ci

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
	./gradlew :engine:app:bootRun

restart: dev

demo: ## the engine with the console Demo: opens http://localhost:$(PORT)/?demo and starts the player (dev auth only, never live)
	$(MAKE) dev OPEN_PATH='/?demo'


test: ## unit + compile-fail + e2e (Testcontainers; needs Docker)
	./gradlew check

hooks: ## install the git hooks (lefthook: pre-commit backend+secrets, pre-push smoke)
	lefthook install

check-placement: ## arch.8: every class sits in a role package (ArchUnit everyClassHasARole, with the layering rules); also part of `./gradlew check`
	./gradlew :engine:app:test --tests 'dev.workflow.app.architecture.ArchitectureTest'

ci: test web-ci ## Gradle check (tests, ArchUnit, detekt, jscpd) + the web gate

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

web-ci: web-gen-check web-typecheck web-lint web-test web-build web-cy web-demo-check web-live-check

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
