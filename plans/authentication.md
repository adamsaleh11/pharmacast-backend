# Plan: Authentication

> Source PRD: `docs/prd/authentication.md`

## Architectural decisions

Durable decisions that apply across all phases:

- **Routes**: `/auth/me` returns current user context; `/auth/logout` acknowledges stateless logout; `/health` and actuator health remain public.
- **Schema**: `app_users.id` is the Supabase Auth user id; first-time signup bootstrap creates `organizations`, `locations`, `app_users`, and `notification_settings`.
- **Key models**: authenticated principal includes `id`, `email`, `organization_id`, and `role`; current-user response includes active `locations[]`.
- **Auth**: Spring Security validates Supabase JWTs with JWKS; Spring loads role and organization from `app_users`.
- **External services**: Supabase Auth issues JWTs and owns sign-in/sign-out session state; Spring Boot does not issue or revoke JWTs.

---

## Phase 1: Token Boundary

**User stories**: 1, 2, 10

### What to build

Configure Spring Security as a stateless resource server that validates Supabase bearer tokens and leaves health endpoints public while protecting application routes.

### Acceptance criteria

- [ ] `/health` returns success without a bearer token.
- [ ] An application route outside public patterns rejects anonymous requests.
- [ ] Supabase JWKS URI is environment-configurable.
- [ ] Token expiration and configured audience are validated.

---

## Phase 2: Typed User Context

**User stories**: 3, 6, 7, 9, 12

### What to build

Resolve a validated Supabase JWT into a typed PharmaForecast principal by loading `app_users`, then expose a helper service that controllers can use without manual claim parsing.

### Acceptance criteria

- [ ] The typed principal exposes `id`, `email`, `organization_id`, and `role`.
- [ ] `organization_id` and `role` come from the database.
- [ ] Valid JWTs without an app profile return `403 USER_PROFILE_NOT_BOOTSTRAPPED`.
- [ ] Backend code has a single supported current-user access service.

---

## Phase 3: Auth API Contract

**User stories**: 3, 8, 11

### What to build

Expose `/auth/me` and `/auth/logout` so the frontend can initialize authenticated app state and perform a documented Supabase-compatible logout flow.

### Acceptance criteria

- [ ] `GET /auth/me` returns `id`, `email`, `role`, `organization_id`, and active `locations[]`.
- [ ] Deactivated locations are omitted from `/auth/me`.
- [ ] `GET /auth/me` without authenticated context returns `401 AUTHENTICATION_REQUIRED`.
- [ ] `POST /auth/logout` returns stateless success and does not claim to revoke Supabase JWTs.

---

## Phase 4: Signup Bootstrap

**User stories**: 4, 5

### What to build

Provide a backend bootstrap endpoint backed by a DB-side function that creates the first organization, location, owner user row, and default notification settings for a first-time Supabase Auth user.

### Acceptance criteria

- [ ] Bootstrap accepts trusted auth user id and email plus frontend metadata.
- [ ] `POST /auth/bootstrap` requires a valid Supabase JWT.
- [ ] Required metadata is `organization_name`, `location_name`, and `location_address`.
- [ ] Created user has role `owner`.
- [ ] Bootstrap can be retried for an already-created user without duplicating the organization.
- [ ] Production Flyway migrations apply cleanly to PostgreSQL.

---

## Phase 5: Contract and Handoff

**User stories**: 5, 6, 9, 11, 12

### What to build

Document the final route, DTO, config, bootstrap, logout, and error contracts so frontend and backend engineers can build on the feature without reading implementation details.

### Acceptance criteria

- [ ] Handoff document lists all public interfaces and response shapes.
- [ ] Security behavior is documented, including `401` and `403` cases.
- [ ] Environment variables and defaults are documented.
- [ ] Known limitations and follow-up work are explicit.
