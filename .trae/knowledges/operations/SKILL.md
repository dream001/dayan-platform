---
name: knowledge-dayan-operations
description: >
  Covers runtime topology, environment wiring, and cross-cutting verification infrastructure.
  Navigate when: changing Docker services, environment variables, health checks, or test containers.
  Excludes: business API internals (see ../backend/) and browser implementation (see ../frontend/).
  Keywords: Docker Compose, PostgreSQL, MinIO, Testcontainers, environment, deployment, tests.
---

## Module Structure

Operations joins local production-like services and integration-test infrastructure.

### Directory Layout
- `docker-compose.yml` — PostgreSQL, MinIO, backend, and frontend services
- `.env.example` — Supported runtime configuration
- `backend/src/test/java/com/dayan/platform/support/` — Shared integration environment

### Key Entry Points
- `docker-compose.yml` — Persistent local stack
- `PostgreSqlIntegrationTestSupport` in `backend/src/test/java/com/dayan/platform/support/PostgreSqlIntegrationTestSupport.java` — Test stack

## Gotchas
- Backend startup depends on both database migration readiness and MinIO bucket initialization, so health ordering must preserve both dependencies (`docker-compose.yml`, `backend/src/main/java/com/dayan/platform/config/MinioBucketInitializer.java`)
- Testcontainers uses a deliberately old pinned MinIO image while PostgreSQL uses version 16; changing either image can alter protocol and migration behavior (`backend/src/test/java/com/dayan/platform/support/PostgreSqlIntegrationTestSupport.java`)

## Architecture
- Environment variables map into typed Spring configuration, keeping secrets out of source while preserving local defaults for non-production use (`backend/src/main/resources/application.yml`, `.env.example`)

## Child Knowledge Nodes
- `./integration-testing/SKILL.md` — Navigate when: writing PostgreSQL/MinIO integration tests or diagnosing suite isolation
