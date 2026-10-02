---
name: knowledge-dayan-backend-audit-data
description: >
  Covers PostgreSQL operation auditing and real-data dashboard aggregation.
  Navigate when: recording writes or login events, filtering logs, adding dashboard metrics, or changing audit schema.
  Excludes: authentication mechanics (see ../identity-access/) and object storage (see ../files/).
  Keywords: operation_log, OperationLog, audit, dashboard, requestId, PostgreSQL, statistics.
---

## Module Structure

Audit data is append-only operational evidence, while dashboard data is aggregate read projection over users, files, and logs.

### Directory Layout
- `backend/src/main/java/com/dayan/platform/model/OperationLog.java` — Audit persistence model
- `backend/src/main/java/com/dayan/platform/repository/mapper/OperationLogMapper.java` — Audit data access
- `backend/src/main/resources/db/migration/` — Audit schema and query indexes
- `backend/src/test/java/com/dayan/platform/` — PostgreSQL-backed contract verification

### Key Entry Points
- `operation_log` in `backend/src/main/resources/db/migration/V1__create_core_schema.sql` — Durable audit record
- `RequestTraceFilter` in `backend/src/main/java/com/dayan/platform/common/trace/RequestTraceFilter.java` — Request correlation boundary

## Gotchas
- Failed audit records must commit independently from failed business transactions; sharing the caller transaction causes the evidence to roll back with the operation (`backend/src/main/resources/db/migration/V1__create_core_schema.sql`)
- Audit details must never serialize request DTOs wholesale because password and refresh-token fields exist in authentication and user-management payloads (`backend/src/main/java/com/dayan/platform/dto/AuthDtos.java`, `backend/src/main/java/com/dayan/platform/dto/RbacDtos.java`)
- Login failures have no authenticated principal, so the normalized submitted username must be retained without treating it as a verified operator identity (`backend/src/main/java/com/dayan/platform/service/impl/AuthServiceImpl.java`)

## Architecture
- Request ID, remote address, and user agent originate at the HTTP boundary, while action outcome and duration surround the business invocation (`backend/src/main/java/com/dayan/platform/common/trace/RequestTraceFilter.java`, `backend/src/main/java/com/dayan/platform/controller/AuthController.java`)
- Dashboard figures must aggregate persisted PostgreSQL rows instead of reusing frontend placeholders or application-memory counters (`backend/src/main/resources/db/migration/V1__create_core_schema.sql`)

## Security Considerations
- Error summaries should expose stable business error information but collapse unexpected exceptions to their type, preventing database or storage internals from entering audit details (`backend/src/main/java/com/dayan/platform/common/exception/GlobalExceptionHandler.java`)
- Audit list and detail are separate authorities in the seeded RBAC model, so detail responses must not be reachable through list permission alone (`backend/src/main/resources/db/migration/V2__seed_rbac_baseline.sql`)
