---
name: knowledge-dayan-operations-integration-testing
description: >
  Covers real PostgreSQL and MinIO integration-test setup, state isolation, and verification patterns.
  Navigate when: adding backend integration tests, diagnosing container startup, or validating migrations.
  Excludes: production deployment configuration (see ../) and frontend unit tests (see ../../frontend/).
  Keywords: Testcontainers, PostgreSqlIntegrationTestSupport, MockMvc, JdbcTemplate, transactional tests.
---

## Module Structure

Backend integration tests share singleton containers and exercise HTTP contracts through MockMvc.

### Directory Layout
- `backend/src/test/java/com/dayan/platform/support/PostgreSqlIntegrationTestSupport.java` — Container properties
- `backend/src/test/java/com/dayan/platform/controller/` — HTTP integration tests
- `backend/src/test/java/com/dayan/platform/repository/` — Migration and mapper tests

### Key Entry Points
- `PostgreSqlIntegrationTestSupport` in `backend/src/test/java/com/dayan/platform/support/PostgreSqlIntegrationTestSupport.java` — Container lifecycle
- `DatabaseMigrationIntegrationTest` in `backend/src/test/java/com/dayan/platform/repository/DatabaseMigrationIntegrationTest.java` — Schema verification

## Gotchas
- Shared containers outlive individual test classes, so each test must reset mutable rows it depends on rather than assuming an empty database (`backend/src/test/java/com/dayan/platform/support/PostgreSqlIntegrationTestSupport.java`, `backend/src/test/java/com/dayan/platform/controller/AuthIntegrationTest.java`)
- Transactional controller tests roll back JDBC and request-thread work only when it joins the test transaction; independent transactions such as durable failure auditing require explicit cleanup (`backend/src/test/java/com/dayan/platform/controller/RbacManagementIntegrationTest.java`)

## Architecture
- Dynamic properties route the full Spring context to real container endpoints before application startup and Flyway migration (`backend/src/test/java/com/dayan/platform/support/PostgreSqlIntegrationTestSupport.java`)

## Patterns
- MockMvc tests authenticate through the real login endpoint and assert both HTTP envelopes and direct PostgreSQL state, avoiding mocked security or persistence (`backend/src/test/java/com/dayan/platform/controller/AuthIntegrationTest.java`, `backend/src/test/java/com/dayan/platform/controller/RbacManagementIntegrationTest.java`)

## Dependencies
- Integration execution requires a Docker-compatible runtime because neither PostgreSQL nor MinIO is replaced with an in-memory fake (`backend/pom.xml`, `backend/src/test/java/com/dayan/platform/support/PostgreSqlIntegrationTestSupport.java`)
