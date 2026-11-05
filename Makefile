.PHONY: up down stop dev restart test ci diagrams web web-demo web-install web-gen-check web-typecheck web-lint web-test web-build web-demo-build web-cy web-smoke web-demo-check web-live-check web-ci share share-local cy-live

PORT ?= 47831
export PORT

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
	      curl -sf http://localhost:$(PORT)/health >/dev/null 2>&1 && { open "http://localhost:$(PORT)/"; exit 0; }; \
	      sleep 1; \
	    done ) & \
	fi; \
	./gradlew :engine:app:bootRun

restart: dev

test: ## unit + compile-fail + e2e (Testcontainers; needs Docker)
	./gradlew check

ci: test web-ci ## Gradle check + the web gate

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

share: ## serve the DEMO build through ngrok (needs NGROK_AUTHTOKEN); remote viewers run the Demo in their own tab
	$(WEB) exec node scripts/share.mjs

share-local: ## serve the demo build on 0.0.0.0:5183 without a tunnel
	$(WEB) exec node scripts/share.mjs --no-tunnel
