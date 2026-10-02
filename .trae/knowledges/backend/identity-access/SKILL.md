---
name: knowledge-dayan-backend-identity-access
description: >
  Covers authentication, session rotation, users, roles, permissions, menus, and departments.
  Navigate when: modifying login, JWT claims, account lifecycle, RBAC, or organization rules.
  Excludes: operation-log persistence (see ../audit-data/) and file storage (see ../files/).
  Keywords: AuthServiceImpl, SecurityConfiguration, RBAC, JWT, refresh token, user, role, menu.
---

## Module Structure

Identity and access combines stateless JWT access tokens with database-backed rotating refresh sessions and permission-code RBAC.

### Directory Layout
- `backend/src/main/java/com/dayan/platform/security/` — Principal loading and JWT authorization
- `backend/src/main/java/com/dayan/platform/service/impl/` — Authentication and RBAC transactions
- `backend/src/main/java/com/dayan/platform/controller/` — Auth and management endpoints

### Key Entry Points
- `AuthServiceImpl.login()` in `backend/src/main/java/com/dayan/platform/service/impl/AuthServiceImpl.java` — Login and token issue
- `SecurityConfiguration.securityFilterChain()` in `backend/src/main/java/com/dayan/platform/config/SecurityConfiguration.java` — Route and JWT enforcement

## Gotchas
- Refresh tokens are stored only as SHA-256 hashes and rotated under a row lock; persisting or logging a raw token breaks the session threat model (`backend/src/main/java/com/dayan/platform/service/impl/AuthServiceImpl.java`, `backend/src/main/java/com/dayan/platform/repository/mapper/AuthSessionMapper.java`)
- Disabling a user revokes all refresh sessions, while changing one’s own password preserves the current session and revokes only the others (`backend/src/main/java/com/dayan/platform/service/impl/UserManagementServiceImpl.java`, `backend/src/main/java/com/dayan/platform/service/impl/AuthServiceImpl.java`)
- Built-in roles cannot be disabled, renamed by code, deleted, or stripped of every user/permission (`backend/src/main/java/com/dayan/platform/service/impl/RoleServiceImpl.java`)

## Architecture
- Access-token authentication reloads the account and authorities from PostgreSQL on every request, so disabled users and permission changes take effect without waiting for token expiry (`backend/src/main/java/com/dayan/platform/config/SecurityConfiguration.java`, `backend/src/main/java/com/dayan/platform/security/PlatformUserDetailsService.java`)
- Dynamic menus include granted visible menus plus enabled ancestor menus, allowing navigation grouping without granting every ancestor explicitly (`backend/src/main/java/com/dayan/platform/repository/mapper/MenuPermissionMapper.java`)

## Security Considerations
- BCrypt’s 72-byte input limit is checked before hashing or matching to avoid silent password truncation (`backend/src/main/java/com/dayan/platform/service/impl/AuthServiceImpl.java`, `backend/src/main/java/com/dayan/platform/service/impl/UserManagementServiceImpl.java`)
- Login deliberately returns the same invalid-credentials error for unknown, disabled, and wrong-password accounts to limit account enumeration (`backend/src/main/java/com/dayan/platform/service/impl/AuthServiceImpl.java`)
