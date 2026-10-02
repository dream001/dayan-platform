---
name: knowledge-dayan-frontend
description: >
  Covers Vue client routing, authentication state, dynamic menus, and UI conventions.
  Navigate when: changing login flow, Axios refresh, route guards, permission directives, or application shell.
  Excludes: backend API implementation (see ../backend/) and container topology (see ../operations/).
  Keywords: Vue 3, Pinia, Axios, router, token session, permission, Element Plus.
---

## Module Structure

The frontend is a Vue 3 SPA with Pinia state, guarded routes, and backend-driven navigation.

### Directory Layout
- `frontend/src/services/` — HTTP, token, authentication, and feedback behavior
- `frontend/src/stores/` — Authentication and layout state
- `frontend/src/router/` — Static guards and dynamic menu routes
- `frontend/src/views/` — Login, status, workspace, and module views

### Key Entry Points
- `frontend/src/main.ts` — App, store, router, and directive registration
- `frontend/src/router/index.ts` — Navigation enforcement
- `frontend/src/stores/auth.ts` — Session lifecycle

## Gotchas
- Access tokens live only in memory while refresh tokens live in local storage; a browser reload must restore through refresh/profile rather than reading an access token (`frontend/src/services/token-session.ts`, `frontend/src/stores/auth.ts`)
- Concurrent 401 responses share one refresh promise and retry each request at most once; bypassing the shared HTTP client can produce rotation races (`frontend/src/services/http.ts`)
- Dynamic routes are registered only after authentication and then rematched, so menu paths must be normalized before route insertion (`frontend/src/router/index.ts`, `frontend/src/router/menu.ts`)

## Architecture
- Frontend permission checks improve navigation and button UX but backend `@PreAuthorize` remains the security boundary (`frontend/src/directives/permission.ts`, `backend/src/main/java/com/dayan/platform/config/SecurityConfiguration.java`)
- Logout always clears local credentials even if the remote revocation call fails, preventing a broken network request from preserving client authentication state (`frontend/src/stores/auth.ts`)

## Testing Strategy
- Vitest resets local storage and the DOM after each test to prevent auth and layout state leaking across cases (`frontend/src/tests/setup.ts`)
