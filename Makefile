.PHONY: up down stop dev restart test ci diagrams

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

ci: test

diagrams: ## regenerate docs/workflows from the DSL
	./gradlew :engine:app:diagrams
