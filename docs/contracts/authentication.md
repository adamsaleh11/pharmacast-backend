# Implementation Handoff Contract

## 1. Summary
- Implemented Supabase JWT validation support in Spring Security for the PharmaForecast backend.
- Added a typed authenticated user context so controllers and services can use `id`, `email`, `organization_id`, and `role` without parsing JWT claims repeatedly.
- Added `/auth/me` and `/auth/logout` API contracts.
- Added DB-side first-owner bootstrap support through `bootstrap_first_owner_user(uuid, text, text, text, text)`.
- Added a PRD at `docs/prd/authentication.md` and implementation plan at `plans/authentication.md`.
- In scope: Spring Security resource-server setup, current-user context, auth endpoints, bootstrap SQL function, behavior tests, and handoff docs.
- Out of scope: Spring-issued JWTs, Supabase Admin logout/revocation, frontend signup UI, full role matrix, Python service changes, and LLM changes.
- Owning repo/service/module: `pharmacast-backend`, Spring Boot backend, packages `ca.pharmaforecast.backend.auth` and `ca.pharmaforecast.backend.config`.

## 2. Files Added or Changed
- `docs/prd/authentication.md` - created - PRD for the authentication feature.
- `plans/authentication.md` - created - tracer-bullet implementation plan for the authentication feature.
- `src/main/java/ca/pharmaforecast/backend/config/SecurityConfig.java` - updated - configures stateless Spring Security, public endpoints, OAuth2 resource server JWT support, and the Supabase user-context filter.
- `src/main/java/ca/pharmaforecast/backend/config/JwtSecurityConfig.java` - created - builds a `JwtDecoder` from Supabase JWKS and validates timestamp, configured issuer, and configured audience.
- `src/main/java/ca/pharmaforecast/backend/config/SupabaseJwtProperties.java` - created - binds `pharmaforecast.security.supabase.*` configuration.
- `src/main/java/ca/pharmaforecast/backend/auth/AuthenticatedUserPrincipal.java` - created - typed principal record with `id`, `email`, `organizationId`, and `role`.
- `src/main/java/ca/pharmaforecast/backend/auth/AppUserAuthenticationToken.java` - created - Spring `Authentication` wrapper for the typed principal and original `Jwt`.
- `src/main/java/ca/pharmaforecast/backend/auth/SupabaseUserContextFilter.java` - created - resolves validated `JwtAuthenticationToken` into an `AppUserAuthenticationToken`.
- `src/main/java/ca/pharmaforecast/backend/auth/CurrentUserService.java` - created - helper service for accessing the current typed principal.
- `src/main/java/ca/pharmaforecast/backend/auth/AuthController.java` - created - exposes `GET /auth/me` and `POST /auth/logout`.
- `src/main/java/ca/pharmaforecast/backend/auth/AuthExceptionHandler.java` - created - maps missing authenticated context to `401 AUTHENTICATION_REQUIRED`.
- `src/main/java/ca/pharmaforecast/backend/auth/UserRepository.java` - updated - adds `findByIdAndEmail(UUID id, String email)`.
- `src/main/java/ca/pharmaforecast/backend/location/LocationRepository.java` - updated - adds `findByOrganizationIdAndDeactivatedAtIsNullOrderByNameAsc(UUID organizationId)`.
- `src/main/java/ca/pharmaforecast/backend/common/BaseEntity.java` - updated - adds explicit getters for `id`, `createdAt`, and `updatedAt`.
- `src/main/java/ca/pharmaforecast/backend/auth/User.java` - updated - adds explicit getters needed by auth code.
- `src/main/java/ca/pharmaforecast/backend/location/Location.java` - updated - adds explicit getters needed by auth code.
- `src/main/resources/application.yml` - updated - adds Supabase JWT configuration keys and environment variable bindings.
- `src/main/resources/db/migration/V2__auth_bootstrap.sql` - created - adds the DB-side bootstrap function.
- `src/test/java/ca/pharmaforecast/backend/AuthEndpointTest.java` - created - behavior tests for auth endpoint and security contracts.
- `src/test/java/ca/pharmaforecast/backend/AuthTestRepositoryConfig.java` - created - test repository doubles without Mockito mocks.
- `src/test/java/ca/pharmaforecast/backend/HealthEndpointTest.java` - updated - uses MockMvc and imports auth test repositories.
- `src/test/java/ca/pharmaforecast/backend/FlywayMigrationTest.java` - updated - adds a Testcontainers-backed bootstrap function verification.
- `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` - created - uses `mock-maker-subclass` for Java 25 test compatibility.

## 3. Public Interface Contract

### `GET /auth/me`
- Type: HTTP endpoint.
- Purpose: Return the current authenticated PharmaForecast user context.
- Owner: Spring Boot backend.
- Inputs:
  - Header `Authorization: Bearer <supabase_access_token>` required for a successful response.
- Outputs:
  - `200 OK` JSON body:
    ```json
    {
      "id": "00000000-0000-0000-0000-000000000000",
      "email": "owner@example.com",
      "role": "owner",
      "organization_id": "11111111-1111-1111-1111-111111111111",
      "locations": [
        {
          "id": "22222222-2222-2222-2222-222222222222",
          "name": "Main Pharmacy",
          "address": "100 Bank St, Ottawa, ON"
        }
      ]
    }
    ```
- Required fields: `id`, `email`, `role`, `organization_id`, `locations`.
- Optional fields: none.
- Validation rules:
  - JWT must be valid according to Spring resource-server JWT validation.
  - JWT `sub` must be a UUID.
  - JWT `email` must be present and nonblank.
  - `app_users` row must exist for `(id = sub, email = email)`.
- Defaults: `locations` is an empty array if the organization has no active locations.
- Status codes and errors:
  - `200 OK` on success.
  - `401 Unauthorized` with `{"error":"AUTHENTICATION_REQUIRED"}` when no authenticated user context exists.
  - `401 Unauthorized` with `{"error":"JWT_SUBJECT_MISSING"}` when JWT subject is missing.
  - `401 Unauthorized` with `{"error":"JWT_SUBJECT_INVALID"}` when JWT subject is not a UUID.
  - `401 Unauthorized` with `{"error":"JWT_EMAIL_MISSING"}` when JWT email is missing or blank.
  - `403 Forbidden` with `{"error":"USER_PROFILE_NOT_BOOTSTRAPPED"}` when the JWT is valid but no matching `app_users` row exists.

### `POST /auth/logout`
- Type: HTTP endpoint.
- Purpose: Stateless backend acknowledgement for frontend logout flow.
- Owner: Spring Boot backend.
- Inputs: no body; no authenticated context required.
- Outputs: `204 No Content`.
- Required fields: none.
- Optional fields: none.
- Validation rules: none.
- Defaults: always stateless; no server-side session is created or destroyed.
- Status codes and errors:
  - `204 No Content` on success.
- Exact server behavior:
  - Spring Boot does not issue JWTs.
  - Spring Boot does not revoke Supabase access tokens or refresh tokens.
  - Spring Boot does not call Supabase Admin APIs.
  - The frontend must call `supabase.auth.signOut()` and discard local session state.

### `bootstrap_first_owner_user(uuid, text, text, text, text)`
- Type: PostgreSQL function.
- Purpose: Bootstrap a first-time Supabase Auth user into PharmaForecast tenant tables.
- Owner: Spring Boot database schema.
- Inputs:
  - `p_auth_user_id uuid` - required - Supabase Auth user id.
  - `p_email text` - required - Supabase Auth user email.
  - `p_organization_name text` - required - frontend signup metadata `organization_name`.
  - `p_location_name text` - required - frontend signup metadata `location_name`.
  - `p_location_address text` - required - frontend signup metadata `location_address`.
- Outputs:
  - Table with `organization_id uuid`, `location_id uuid`, `user_id uuid`.
- Required fields: all inputs are required.
- Optional fields: none.
- Validation rules:
  - Null UUID is rejected.
  - Blank email, organization name, location name, and location address are rejected.
  - Created `app_users.role` is always `owner`.
  - Email is stored as lowercase trimmed text.
- Defaults:
  - `notification_settings` is created with existing table defaults.
- Result states:
  - New user: creates `organizations`, `locations`, `app_users`, and `notification_settings`, then returns ids.
  - Existing user id: returns existing organization id, first active location id, and user id without creating a duplicate organization.

## 4. Data Contract

### `AuthenticatedUserPrincipal`
- Type: Java record.
- Fields:
  - `id UUID` - required - Supabase/Auth app user id.
  - `email String` - required - authenticated email.
  - `organizationId UUID` - required - tenant id from `app_users.organization_id`.
  - `role UserRole` - required - role from `app_users.role`.
- Allowed `role` values: `owner`, `admin`, `pharmacist`, `staff`.
- Backward compatibility: new internal type; no existing API removed.

### `AuthController.CurrentUserResponse`
- Type: Java record serialized to JSON.
- Fields:
  - `id UUID` - required.
  - `email String` - required.
  - `role UserRole` - required.
  - `organization_id UUID` - required JSON field from Java `organizationId`.
  - `locations List<LocationResponse>` - required.

### `AuthController.LocationResponse`
- Type: Java record serialized to JSON.
- Fields:
  - `id UUID` - required.
  - `name String` - required.
  - `address String` - required.

### Database Function `bootstrap_first_owner_user`
- Migration: `V2__auth_bootstrap.sql`.
- Tables written:
  - `organizations.name`.
  - `locations.organization_id`, `locations.name`, `locations.address`.
  - `app_users.id`, `app_users.organization_id`, `app_users.email`, `app_users.role`.
  - `notification_settings.organization_id`.
- Backward compatibility: additive migration; no table or column removed.

## 5. Integration Contract
- Upstream dependency: Supabase Auth issues bearer JWTs.
- Upstream dependency: Supabase JWKS endpoint configured by `SUPABASE_JWKS_URI`.
- Downstream dependency: Spring Data JPA repositories `UserRepository` and `LocationRepository`.
- Services called: no external service call is made by `/auth/me` or `/auth/logout`.
- Events consumed/published: none.
- Files read/written at runtime: no application files are read or written at runtime by this feature.
- Environment assumptions:
  - Supabase JWT `sub` equals `app_users.id`.
  - Supabase JWT includes an `email` claim.
  - `app_users` is created by bootstrap before normal protected API use.
- Auth assumptions:
  - JWT identity is trusted after JWKS validation.
  - Authorization context is loaded from the database, not from custom JWT role/org claims.
- Retry behavior:
  - `bootstrap_first_owner_user` can be retried for an existing `p_auth_user_id`; it returns existing tenant ids where possible.
- Timeout behavior: no explicit timeout was added.
- Fallback behavior:
  - Missing app profile returns `403 USER_PROFILE_NOT_BOOTSTRAPPED`.
- Idempotency behavior:
  - `POST /auth/logout` is idempotent and stateless.
  - `bootstrap_first_owner_user` is idempotent for an existing user id.

## 6. Usage Instructions for Other Engineers
- Use `CurrentUserService.requireCurrentUser()` in backend code that needs the authenticated user.
- Depend on `AuthenticatedUserPrincipal.id()` for the Supabase user id.
- Depend on `AuthenticatedUserPrincipal.organizationId()` for tenant scoping.
- Depend on `AuthenticatedUserPrincipal.role()` for future role checks.
- Do not parse JWT claims manually in controllers or services.
- Frontend should call `GET /auth/me` after Supabase sign-in to initialize app state.
- Frontend should handle:
  - loading state while `/auth/me` is pending;
  - success with `locations[]`;
  - `401 AUTHENTICATION_REQUIRED` as not signed in;
  - `403 USER_PROFILE_NOT_BOOTSTRAPPED` as onboarding/bootstrap incomplete.
- Frontend should call `supabase.auth.signOut()` for actual logout and may call `POST /auth/logout` for backend-compatible flow acknowledgement.
- Signup must collect `organization_name`, `location_name`, and `location_address`.
- What is finalized: routes, DTO field names, bootstrap function name, config key names, and missing-profile error code.
- What is provisional: exact caller of `bootstrap_first_owner_user`; no Spring endpoint was added for bootstrap.
- What must not be changed without coordination: using DB `app_users` as authorization source of truth rather than JWT custom role/org claims.

## 7. Security and Authorization Notes
- All non-public routes require bearer authentication.
- Public route patterns are `/health`, `/actuator/health`, `/actuator/health/**`, and `/auth/**`.
- `/auth/me` is path-public but behavior-protected by requiring `CurrentUserService.requireCurrentUser()`.
- `organization_id` and `role` are loaded from `app_users`.
- JWT custom claims for role or organization are not trusted by this implementation.
- Missing app profile returns `403 USER_PROFILE_NOT_BOOTSTRAPPED`.
- `patient_id` is not read, logged, returned, or added to auth context.
- `POST /auth/logout` does not revoke tokens; Supabase client logout is required.
- Multi-tenant isolation still requires server-side organization/location validation in future domain endpoints.
- RLS remains a defense-in-depth layer, not the only authorization control.

## 8. Environment and Configuration
- `SUPABASE_JWKS_URI`
  - Purpose: Supabase JWKS endpoint used by `NimbusJwtDecoder`.
  - Required or optional: optional in local config, required for real Supabase environments.
  - Default: `http://localhost:54321/auth/v1/.well-known/jwks.json`.
  - Dev vs prod: set to `https://<project-ref>.supabase.co/auth/v1/.well-known/jwks.json` in deployed environments.
- `SUPABASE_JWT_ISSUER`
  - Purpose: optional issuer validator.
  - Required or optional: optional.
  - Default: blank, which disables issuer validation.
  - Dev vs prod: should be set in production when the Supabase issuer value is finalized.
- `SUPABASE_JWT_AUDIENCE`
  - Purpose: accepted JWT audience.
  - Required or optional: optional.
  - Default: `authenticated`.
  - Dev vs prod: keep `authenticated` unless Supabase project settings differ.
- `pharmaforecast.security.supabase.jwk-set-uri`
  - Purpose: Spring config key bound from `SUPABASE_JWKS_URI`.
- `pharmaforecast.security.supabase.issuer`
  - Purpose: Spring config key bound from `SUPABASE_JWT_ISSUER`.
- `pharmaforecast.security.supabase.audiences`
  - Purpose: Spring config list containing accepted audiences.

## 9. Testing and Verification
- Added `AuthEndpointTest`.
  - Verifies protected routes reject anonymous requests.
  - Verifies `/auth/me` without auth returns `401 AUTHENTICATION_REQUIRED`.
  - Verifies `/auth/me` returns database-backed user and active location data.
  - Verifies valid JWT without app user returns `403 USER_PROFILE_NOT_BOOTSTRAPPED`.
  - Verifies `/auth/logout` returns `204 No Content`.
- Updated `HealthEndpointTest`.
  - Verifies `/health` remains public and returns `status` plus `timestamp`.
- Updated `FlywayMigrationTest`.
  - Adds Testcontainers-backed verification for `bootstrap_first_owner_user`.
- Added `AuthTestRepositoryConfig`.
  - Provides JDK proxy repositories for HTTP tests without Mockito mock beans.
- Added `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`.
  - Uses `mock-maker-subclass` to avoid Java 25 inline mock self-attach failures.
- Verification command run:
  - `mvn -Dtest=AuthEndpointTest,HealthEndpointTest test` - passed, 6 tests.
  - `mvn test` - passed, 9 tests total, 3 Testcontainers tests skipped because Docker was not available in the sandbox.
- Known test gap:
  - The bootstrap SQL function test was skipped locally because Docker/Testcontainers could not access Docker. It should run in an environment with Docker available.

## 10. Known Limitations and TODOs
- NOT IMPLEMENTED: Supabase Admin API token revocation.
- NOT IMPLEMENTED: Spring endpoint for invoking `bootstrap_first_owner_user`.
- NOT IMPLEMENTED: full role matrix beyond preserving current `UserRole` values.
- NOT IMPLEMENTED: frontend signup UI or Supabase metadata write.
- TODO: decide whether bootstrap is invoked by a backend endpoint, Supabase RPC, or admin script.
- TODO: set `SUPABASE_JWT_ISSUER` in production once the exact issuer is confirmed.
- TODO: run Testcontainers migration tests in a Docker-enabled environment.
- Compatibility risk: Java 25 required `mock-maker-subclass` for tests; project target remains Java 21.
- Compatibility risk: normal JPA inserts for `app_users` still rely on DB-generated ids unless code explicitly inserts ids through SQL/bootstrap.

## 11. Source of Truth Snapshot
- Final route names: `GET /auth/me`, `POST /auth/logout`.
- Final DB function name: `bootstrap_first_owner_user(uuid, text, text, text, text)`.
- Final principal type: `AuthenticatedUserPrincipal`.
- Final authentication type: `AppUserAuthenticationToken`.
- Final helper service: `CurrentUserService`.
- Final response DTOs: `CurrentUserResponse`, `LocationResponse`.
- Final enum values: `owner`, `admin`, `pharmacist`, `staff`.
- Final error codes: `AUTHENTICATION_REQUIRED`, `JWT_SUBJECT_MISSING`, `JWT_SUBJECT_INVALID`, `JWT_EMAIL_MISSING`, `USER_PROFILE_NOT_BOOTSTRAPPED`.
- Final config keys: `pharmaforecast.security.supabase.jwk-set-uri`, `pharmaforecast.security.supabase.issuer`, `pharmaforecast.security.supabase.audiences`.
- Final environment variables: `SUPABASE_JWKS_URI`, `SUPABASE_JWT_ISSUER`, `SUPABASE_JWT_AUDIENCE`.
- Key implementation files:
  - `src/main/java/ca/pharmaforecast/backend/config/SecurityConfig.java`
  - `src/main/java/ca/pharmaforecast/backend/config/JwtSecurityConfig.java`
  - `src/main/java/ca/pharmaforecast/backend/auth/SupabaseUserContextFilter.java`
  - `src/main/java/ca/pharmaforecast/backend/auth/AuthController.java`
  - `src/main/resources/db/migration/V2__auth_bootstrap.sql`
- Breaking changes from previous version:
  - Routes outside public patterns now require bearer authentication instead of being denied unconditionally.

## 12. Copy-Paste Handoff for the Next Engineer
Authentication is implemented for the Spring Boot backend using Supabase JWKS validation and a database-backed typed user principal. It is safe to depend on `GET /auth/me`, `POST /auth/logout`, `CurrentUserService.requireCurrentUser()`, and the `bootstrap_first_owner_user` SQL function names and shapes documented here.

What remains to be built: a concrete bootstrap caller, production Supabase issuer configuration, and role-specific authorization rules for future domain endpoints. The main trap is not to trust JWT custom role or organization claims for authorization; load tenant context from `app_users` through the existing user-context flow.

Read sections 3, 7, and 8 first before building frontend auth wiring or adding protected backend endpoints.
