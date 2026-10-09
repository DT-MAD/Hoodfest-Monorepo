# Pit Stop server.
#
# Configuration comes from the environment; `make run` loads .env if present.
# Start from .env.example:  cp .env.example .env

GO      ?= go
PKG     := ./...
BINARY  := bin/pitstop-server

# A throwaway Postgres for development and the store tests.
PG_CONTAINER := pitstop-pg
PG_PORT      ?= 55432
PG_URL       := postgres://pitstop:pitstop@localhost:$(PG_PORT)/pitstop?sslmode=disable

.DEFAULT_GOAL := help

## help: list the available targets
help:
	@grep -E '^## ' $(MAKEFILE_LIST) | sed 's/## /  /'

## run: start the server, loading .env if it exists
run:
	@set -a; [ -f .env ] && . ./.env; set +a; $(GO) run ./cmd/server

## build: compile the server to bin/
build:
	$(GO) build -o $(BINARY) ./cmd/server

## test: run every test (Postgres-backed ones skip without TEST_DATABASE_URL)
test:
	$(GO) test $(PKG)

## test-all: run every test against the development database
test-all: db-wait
	TEST_DATABASE_URL="$(PG_URL)" $(GO) test $(PKG)

## test-race: run the full suite under the race detector
test-race: db-wait
	TEST_DATABASE_URL="$(PG_URL)" $(GO) test -race $(PKG)

## check: format, vet and test
check: fmt vet test

## fmt: format every Go file
fmt:
	$(GO) fmt $(PKG)

## vet: run go vet
vet:
	$(GO) vet $(PKG)

## db-up: start a development Postgres in Docker
db-up:
	@docker start $(PG_CONTAINER) 2>/dev/null || \
	docker run -d --name $(PG_CONTAINER) \
		-e POSTGRES_USER=pitstop -e POSTGRES_PASSWORD=pitstop -e POSTGRES_DB=pitstop \
		-p $(PG_PORT):5432 postgres:17-alpine
	@echo "DATABASE_URL=$(PG_URL)"

## db-wait: block until the development Postgres accepts connections
db-wait: db-up
	@for i in $$(seq 1 30); do \
		docker exec $(PG_CONTAINER) pg_isready -U pitstop >/dev/null 2>&1 && exit 0; \
		sleep 1; \
	done; \
	echo "Postgres did not become ready" >&2; exit 1

## db-down: stop and remove the development Postgres
db-down:
	-docker rm -f $(PG_CONTAINER)

## db-shell: open psql against the development database
db-shell:
	docker exec -it $(PG_CONTAINER) psql -U pitstop -d pitstop

## dev: start Postgres and run the server against it
dev: db-wait
	DATABASE_URL="$(PG_URL)" \
	API_KEY="$${API_KEY:-dev-app-key}" \
	ADMIN_KEY="$${ADMIN_KEY:-dev-admin-key}" \
	$(GO) run ./cmd/server

## clean: remove build output
clean:
	rm -rf bin

.PHONY: help run build test test-all test-race check fmt vet db-up db-wait db-down db-shell dev clean
