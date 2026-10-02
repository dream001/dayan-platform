---
name: knowledge-dayan-platform
description: >
  Covers the Dayan administration platform architecture and repository-wide conventions.
  Navigate when: locating a feature, assessing cross-layer impact, or understanding the project.
  Excludes: detailed backend flows (see backend/) and frontend behavior (see frontend/).
  Keywords: Dayan, Spring Boot, Vue, PostgreSQL, MinIO, architecture, repository.
---

## Module Structure

The repository is a backend/frontend monorepo deployed with PostgreSQL and MinIO.

### Directory Layout
- `backend/` — Java 21 Spring Boot API and integration tests
- `frontend/` — Vue 3 TypeScript administration client
- `docker-compose.yml` — Local service topology and persistence
- `.trae/specs/build-admin-platform/` — Product requirements and task checklist

### Key Entry Points
- `backend/src/main/java/com/dayan/platform/DayanPlatformApplication.java` — Backend bootstrap
- `frontend/src/main.ts` — Frontend bootstrap
- `docker-compose.yml` — Full-stack runtime bootstrap

## Gotchas
- The backend integration suite starts real PostgreSQL and MinIO containers, so Docker availability is part of the test contract (`backend/src/test/java/com/dayan/platform/support/PostgreSqlIntegrationTestSupport.java`)
- Database structure is migration-owned; model changes without a Flyway migration leave clean deployments inconsistent (`backend/src/main/resources/db/migration/V1__create_core_schema.sql`)

## Architecture
- Browser requests use a versioned API, Spring Security JWT authorization, MyBatis-backed PostgreSQL persistence, and MinIO object storage (`frontend/src/services/http.ts`, `backend/src/main/java/com/dayan/platform/config/SecurityConfiguration.java`)
- RBAC menu permissions are both backend authorities and frontend navigation inputs, coupling seeded permission codes to route visibility (`backend/src/main/resources/db/migration/V2__seed_rbac_baseline.sql`, `frontend/src/router/menu.ts`)

## Patterns
- User-visible backend responses share one envelope and request identifier, while binary file downloads explicitly opt out (`backend/src/main/java/com/dayan/platform/common/api/ApiResponseAdvice.java`, `backend/src/main/java/com/dayan/platform/common/api/RawResponse.java`)

## Child Knowledge Nodes
- `./backend/SKILL.md` — Navigate when: changing APIs, persistence, authentication, audit, or backend tests
- `./frontend/SKILL.md` — Navigate when: changing routing, session state, permissions, or UI behavior
- `./operations/SKILL.md` — Navigate when: changing deployment topology or cross-cutting test infrastructure

## Generation Metadata
- Generated: 2026-10-02
- Nodes: 8; maximum tree depth: 2
- Coverage: backend, frontend, deployment, database, security, and testing domains
