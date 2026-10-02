---
name: knowledge-dayan-backend
description: >
  Covers backend layering, API contracts, persistence, and security boundaries.
  Navigate when: implementing controllers, services, mappers, migrations, or integration tests.
  Excludes: browser behavior (see ../frontend/) and deployment details (see ../operations/).
  Keywords: Spring Boot, Java 21, MyBatis-Plus, PostgreSQL, Controller, Service, Mapper.
---

## Module Structure

The backend uses controller, service, mapper, model, DTO, and VO layers.

### Directory Layout
- `backend/src/main/java/com/dayan/platform/` — Application source
- `backend/src/main/resources/db/migration/` — PostgreSQL Flyway migrations
- `backend/src/test/java/com/dayan/platform/` — Integration and unit tests

### Key Entry Points
- `backend/src/main/java/com/dayan/platform/DayanPlatformApplication.java` — Application entry
- `backend/src/main/java/com/dayan/platform/config/SecurityConfiguration.java` — Security chain
- `backend/src/main/resources/application.yml` — Runtime settings

## Gotchas
- Method security is the authoritative authorization layer; adding an authenticated route without `@PreAuthorize` can unintentionally grant every enabled user access (`backend/src/main/java/com/dayan/platform/config/SecurityConfiguration.java`)
- PostgreSQL-specific SQL features such as recursive CTEs and JSON aggregation make H2 an invalid substitute for integration verification (`backend/src/main/java/com/dayan/platform/repository/mapper/MenuPermissionMapper.java`, `backend/src/main/java/com/dayan/platform/repository/mapper/UserAccountMapper.java`)

## Architecture
- Controllers own transport concerns, services own transactional business behavior, and annotated mappers own PostgreSQL queries (`backend/src/main/java/com/dayan/platform/controller/UserManagementController.java`, `backend/src/main/java/com/dayan/platform/service/impl/UserManagementServiceImpl.java`)
- File operations intentionally coordinate independent MinIO and PostgreSQL resources with explicit compensation rather than pretending to provide a distributed transaction (`backend/src/main/java/com/dayan/platform/service/impl/FileServiceImpl.java`)

## Patterns
- Domain failures use `BusinessException` and stable `ErrorCode` values so controller contracts do not expose infrastructure exceptions (`backend/src/main/java/com/dayan/platform/common/exception/BusinessException.java`, `backend/src/main/java/com/dayan/platform/common/api/ErrorCode.java`)

## Child Knowledge Nodes
- `./identity-access/SKILL.md` — Navigate when: changing login, JWT, users, roles, menus, or departments
- `./audit-data/SKILL.md` — Navigate when: changing operation logs, dashboard statistics, migrations, or PostgreSQL queries
- `./files/SKILL.md` — Navigate when: changing upload, download, preview, deletion, or MinIO compensation
