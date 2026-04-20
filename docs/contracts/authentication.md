# Implementation Handoff Contract

## 1. Summary
- What was implemented: Supabase JWT authentication for the Spring Boot backend, database-backed current-user mapping, `/auth/me`, `/auth/logout`, `/auth/bootstrap`, and a first-owner signup bootstrap function.
- Why it was implemented: Supabase Auth is the source of truth for identity, while Spring Boot remains the orchestration and enforcement layer for PharmaForecast tenant/user context.
- What is in scope:
  - Spring Security resource server configuration.
  - Supabase JWT validation through JWKS.
  - JWT timestamp, optional issuer, and audience validation.
  - Public endpoints `/health`, `/actuator/health`, `/actuator/health/**`, and `/auth/**`.
  - Bearer-token protection for every other route.
  - Typed authenticated principal with `id`, `email`, `organizationId`, and `role`.
  - Current-user helper service.
  - Database-backed lookup of `app_users` by JWT `sub` and `email`.
  - `GET /auth/me`.
  - `POST /auth/logout`.
  - `POST /auth/bootstrap`.
  - PostgreSQL bootstrap function `bootstrap_first_owner_user(uuid, text, text, text, text)`.
  - Hosted Supabase setup guide for retrieving environment values and using the Supabase Dashboard UI.
  - Production Supabase pooler compatibility using `prepareThreshold=0`.
  - Flyway hosted Supabase baseline support using `baseline-on-migrate: true` and `baseline-version: 0`.
- What is out of scope:
  - Frontend signup UI.
  - Frontend Supabase client implementation.
  - Supabase Admin API logout/token revocation.
  - Role authorization matrix beyond carrying `UserRole.owner`.
  - Organization/location authorization checks for domain endpoints other than current-user context.
  - Fly.io deployment.
  - Python forecasting service changes.
  - Python LLM service changes.
  - Stripe, Resend, notification jobs, CSV upload, forecast generation, and purchase-order workflows.
- Which repo/service/module owns this implementation: `/Users/adamsaleh/Downloads/pharmacast-backend`, Spring Boot backend, packages `ca.pharmaforecast.backend.auth` and `ca.pharmaforecast.backend.config`.

## 2. Files Added or Changed
- `/Users/adamsaleh/Downloads/pharmacast-backend/src/main/java/ca/pharmaforecast/backend/config/SecurityConfig.java` - updated - configures stateless Spring Security, resource-server JWT auth, public endpoint matchers, bearer-token protection, CORS defaults, and `SupabaseUserContextFilter`.
- `/Users/adamsaleh/Downloads/pharmacast-backend/src/main/java/ca/pharmaforecast/backend/config/JwtSecurityConfig.java` - created - builds the `JwtDecoder` with `NimbusJwtDecoder.withJwkSetUri(...)` and validators for timestamp, optional issuer, and configured audience.
- `/Users/adamsaleh/Downloads/pharmacast-backend/src/main/java/ca/pharmaforecast/backend/config/SupabaseJwtProperties.java` - created - binds `pharmaforecast.security.supabase.jwk-set-uri`, `pharmaforecast.security.supabase.issuer`, and `pharmaforecast.security.supabase.audiences`.
- `/Users/adamsaleh/Downloads/pharmacast-backend/src/main/java/ca/pharmaforecast/backend/auth/AuthenticatedUserPrincipal.java` - created - custom authenticated principal record with `id`, `email`, `organizationId`, and `role`.
- `/Users/adamsaleh/Downloads/pharmacast-backend/src/main/java/ca/pharmaforecast/backend/auth/AppUserAuthenticationToken.java` - created - Spring `Authentication` implementation that carries `AuthenticatedUserPrincipal` and the original `Jwt`.
- `/Users/adamsaleh/Downloads/pharmacast-backend/src/main/java/ca/pharmaforecast/backend/auth/SupabaseUserContextFilter.java` - created - maps a validated `JwtAuthenticationToken` to app user context by loading `app_users`.
- `/Users/adamsaleh/Downloads/pharmacast-backend/src/main/java/ca/pharmaforecast/backend/auth/CurrentUserService.java` - created - exposes `requireCurrentUser()` so application code does not manually parse JWT claims.
- `/Users/adamsaleh/Downloads/pharmacast-backend/src/main/java/ca/pharmaforecast/backend/auth/AuthController.java` - created - exposes `GET /auth/me`, `POST /auth/logout`, and `POST /auth/bootstrap`.
- `/Users/adamsaleh/Downloads/pharmacast-backend/src/main/java/ca/pharmaforecast/backend/auth/AuthBootstrapService.java` - created - service contract for first-owner bootstrap.
- `/Users/adamsaleh/Downloads/pharmacast-backend/src/main/java/ca/pharmaforecast/backend/auth/AuthBootstrapConfiguration.java` - created - explicit Spring configuration that provides an `AuthBootstrapService` bean backed by `JdbcAuthBootstrapService` when no other `AuthBootstrapService` bean exists.
- `/Users/adamsaleh/Downloads/pharmacast-backend/src/main/java/ca/pharmaforecast/backend/auth/JdbcAuthBootstrapService.java` - created - JDBC implementation that calls `bootstrap_first_owner_user`.
- `/Users/adamsaleh/Downloads/pharmacast-backend/src/main/java/ca/pharmaforecast/backend/auth/AuthExceptionHandler.java` - created - maps `AuthenticationCredentialsNotFoundException` to `401` with `{"error":"AUTHENTICATION_REQUIRED"}`.
- `/Users/adamsaleh/Downloads/pharmacast-backend/src/main/java/ca/pharmaforecast/backend/auth/UserRepository.java` - updated - adds `Optional<User> findByIdAndEmail(UUID id, String email)`.
- `/Users/adamsaleh/Downloads/pharmacast-backend/src/main/java/ca/pharmaforecast/backend/location/LocationRepository.java` - updated - adds `List<Location> findByOrganizationIdAndDeactivatedAtIsNullOrderByNameAsc(UUID organizationId)`.
- `/Users/adamsaleh/Downloads/pharmacast-backend/src/main/java/ca/pharmaforecast/backend/common/BaseEntity.java` - updated - adds explicit getters for `id`, `createdAt`, and `updatedAt`.
- `/Users/adamsaleh/Downloads/pharmacast-backend/src/main/java/ca/pharmaforecast/backend/auth/User.java` - updated - adds explicit getters used by auth code.
- `/Users/adamsaleh/Downloads/pharmacast-backend/src/main/java/ca/pharmaforecast/backend/location/Location.java` - updated - adds explicit getters used by auth code.
- `/Users/adamsaleh/Downloads/pharmacast-backend/src/main/resources/application.yml` - updated - adds Supabase JWT config, production datasource env bindings, Supabase pooler `prepareThreshold=0`, and hosted Supabase Flyway baseline settings.
- `/Users/adamsaleh/Downloads/pharmacast-backend/src/main/resources/db/migration/V2__auth_bootstrap.sql` - created - adds `bootstrap_first_owner_user(uuid, text, text, text, text)`.
- `/Users/adamsaleh/Downloads/pharmacast-backend/src/test/java/ca/pharmaforecast/backend/AuthEndpointTest.java` - created/updated - behavior tests for auth routes, protected routes, missing user profile, logout, and bootstrap.
- `/Users/adamsaleh/Downloads/pharmacast-backend/src/test/java/ca/pharmaforecast/backend/AuthTestRepositoryConfig.java` - created/updated - MOCKED test repositories and bootstrap service using JDK proxies/test doubles.
- `/Users/adamsaleh/Downloads/pharmacast-backend/src/test/java/ca/pharmaforecast/backend/HealthEndpointTest.java` - updated - verifies `/health` with MockMvc.
- `/Users/adamsaleh/Downloads/pharmacast-backend/src/test/java/ca/pharmaforecast/backend/FlywayMigrationTest.java` - updated - contains Testcontainers-backed Flyway/bootstrap verification; skipped when Docker is unavailable.
- `/Users/adamsaleh/Downloads/pharmacast-backend/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` - created - configures Mockito `mock-maker-subclass` for Java 25 compatibility in this local environment.
- `/Users/adamsaleh/Downloads/pharmacast-backend/.env.example` - created - non-secret hosted Supabase environment template.
- `/Users/adamsaleh/Downloads/pharmacast-backend/.gitignore` - updated - ignores `.env` and `.env.*`, while allowing `.env.example`.
- `/Users/adamsaleh/Downloads/pharmacast-backend/docs/setup/supabase-hosted.md` - created - beginner hosted Supabase setup guide.
- `/Users/adamsaleh/Downloads/pharmacast-backend/docs/prd/authentication.md` - created/updated - PRD for this feature.
- `/Users/adamsaleh/Downloads/pharmacast-backend/plans/authentication.md` - created/updated - implementation plan for this feature.
- `/Users/adamsaleh/Downloads/pharmacast-backend/supabase/config.toml` - created by `supabase init` - local Supabase CLI config; hosted Supabase Dashboard setup does not require running local Supabase.
- `/Users/adamsaleh/Downloads/pharmacast-backend/supabase/.gitignore` - created by `supabase init` - ignores local Supabase CLI temp files.
- `/Users/adamsaleh/Downloads/pharmacast-backend/supabase/.temp/cli-latest` - created by Supabase CLI - local CLI temp metadata; should not be relied on by application code.
- `/Users/adamsaleh/Downloads/pharmacast-backend/docs/contracts/authentication.md` - updated - this implementation handoff contract.

## 3. Public Interface Contract

### `GET /auth/me`
- Name: `GET /auth/me`.
- Type: HTTP endpoint.
- Purpose: Return the current authenticated PharmaForecast application user context.
- Owner: Spring Boot backend.
- Inputs:
  - Header `Authorization: Bearer <supabase_access_token>`.
- Outputs:
  - `200 OK` JSON with `id`, `email`, `role`, `organization_id`, and `locations`.
- Required fields:
  - Request header: `Authorization`.
  - Response: `id`, `email`, `role`, `organization_id`, `locations`.
- Optional fields: none.
- Validation rules:
  - Supabase JWT must pass Spring resource-server validation.
  - JWT `sub` must be present.
  - JWT `sub` must parse as `UUID`.
  - JWT `email` must be present and nonblank.
  - `app_users` must contain a row where `id = JWT sub` and `email = JWT email`.
- Defaults:
  - `locations` is an empty array when no active locations exist for the organization.
  - Returned locations are limited to `deactivated_at IS NULL`.
  - Returned locations are ordered by name ascending through `findByOrganizationIdAndDeactivatedAtIsNullOrderByNameAsc`.
- Status codes or result states:
  - `200 OK` on success.
  - `401 Unauthorized` when no authenticated application user context exists.
  - `401 Unauthorized` when JWT `sub` is missing.
  - `401 Unauthorized` when JWT `sub` is not a UUID.
  - `401 Unauthorized` when JWT `email` is missing or blank.
  - `403 Forbidden` when the JWT is valid but no matching `app_users` row exists.
- Error shapes:
  - `{"error":"AUTHENTICATION_REQUIRED"}`
  - `{"error":"JWT_SUBJECT_MISSING"}`
  - `{"error":"JWT_SUBJECT_INVALID"}`
  - `{"error":"JWT_EMAIL_MISSING"}`
  - `{"error":"USER_PROFILE_NOT_BOOTSTRAPPED"}`
- Example input:
  ```http
  GET /auth/me
  Authorization: Bearer <supabase_access_token>
  ```
- Example output:
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

### `POST /auth/bootstrap`
- Name: `POST /auth/bootstrap`.
- Type: HTTP endpoint.
- Purpose: Create or return the initial owner tenant shape for a newly signed-up Supabase Auth user.
- Owner: Spring Boot backend.
- Inputs:
  - Header `Authorization: Bearer <supabase_access_token>`.
  - JSON body:
    ```json
    {
      "organization_name": "Main Pharmacy",
      "location_name": "Main Pharmacy - Bank",
      "location_address": "100 Bank St, Ottawa, ON"
    }
    ```
- Outputs:
  - `200 OK` JSON:
    ```json
    {
      "organization_id": "11111111-1111-1111-1111-111111111111",
      "location_id": "22222222-2222-2222-2222-222222222222",
      "user_id": "00000000-0000-0000-0000-000000000000"
    }
    ```
- Required fields:
  - Request header: `Authorization`.
  - Request body: `organization_name`, `location_name`, `location_address`.
  - Response body: `organization_id`, `location_id`, `user_id`.
- Optional fields: none.
- Validation rules:
  - Supabase JWT must pass Spring resource-server validation.
  - Endpoint requires `Authentication` to be a `JwtAuthenticationToken`.
  - JWT `sub` must be present and parseable as `UUID`.
  - JWT `email` must be present and nonblank.
  - `organization_name`, `location_name`, and `location_address` use `@NotBlank`.
  - SQL function also rejects null or blank required inputs.
- Defaults:
  - Created `app_users.role` is `owner`.
  - Created `notification_settings` uses database defaults from the foundation schema.
- Status codes or result states:
  - `200 OK` for first bootstrap.
  - `200 OK` for retry when an `app_users` row already exists for the JWT subject.
  - `400 Bad Request` for `@NotBlank` validation failures.
  - `401 Unauthorized` with `{"error":"AUTHENTICATION_REQUIRED"}` when no JWT authentication exists or JWT subject/email is unusable.
- Error shapes:
  - `{"error":"AUTHENTICATION_REQUIRED"}` for missing/invalid bootstrap authentication context.
  - Spring MVC validation response body for `@NotBlank` request body failures.
  - Spring database exception response body if `bootstrap_first_owner_user` raises a PostgreSQL exception.
- Example input:
  ```http
  POST /auth/bootstrap
  Authorization: Bearer <supabase_access_token>
  Content-Type: application/json

  {
    "organization_name": "Main Pharmacy",
    "location_name": "Main Pharmacy - Bank",
    "location_address": "100 Bank St, Ottawa, ON"
  }
  ```
- Example output:
  ```json
  {
    "organization_id": "11111111-1111-1111-1111-111111111111",
    "location_id": "22222222-2222-2222-2222-222222222222",
    "user_id": "00000000-0000-0000-0000-000000000000"
  }
  ```

### `POST /auth/logout`
- Name: `POST /auth/logout`.
- Type: HTTP endpoint.
- Purpose: Stateless backend acknowledgement for the frontend logout flow.
- Owner: Spring Boot backend.
- Inputs: no body required.
- Outputs: `204 No Content`.
- Required fields: none.
- Optional fields: none.
- Validation rules: none in the backend controller.
- Defaults:
  - Spring Security permits `/auth/**`, so this endpoint is public.
  - Backend does not maintain an HTTP session.
- Status codes or result states:
  - `204 No Content`.
- Error shapes: no normal error body from this controller method.
- Example input:
  ```http
  POST /auth/logout
  ```
- Example output: empty response body.
- Exact server behavior:
  - Spring Boot does not issue Supabase JWTs.
  - Spring Boot does not revoke Supabase access tokens.
  - Spring Boot does not revoke Supabase refresh tokens.
  - Spring Boot does not call Supabase Admin APIs.
  - Frontend must call `supabase.auth.signOut()` and discard local session state.

### `GET /health`
- Name: `GET /health`.
- Type: HTTP endpoint.
- Purpose: Public backend health response.
- Owner: Spring Boot backend.
- Inputs: none.
- Outputs: JSON health response from `HealthController`.
- Required fields: none.
- Optional fields: none.
- Validation rules: none.
- Defaults: public by Spring Security configuration.
- Status codes or result states:
  - `200 OK` when the controller succeeds.
- Error shapes: NOT IMPLEMENTED by this feature.
- Example input:
  ```http
  GET /health
  ```
- Example output: exact response shape is owned by `HealthController`, not this auth feature.

### `bootstrap_first_owner_user(uuid, text, text, text, text)`
- Name: `bootstrap_first_owner_user`.
- Type: PostgreSQL function.
- Purpose: Create or return the first-time Supabase Auth user tenant shape.
- Owner: Spring Boot database schema managed by Flyway.
- Inputs:
  - `p_auth_user_id uuid` - required - Supabase Auth user id from JWT `sub`.
  - `p_email text` - required - Supabase Auth user email from JWT `email`.
  - `p_organization_name text` - required - `organization_name` from `POST /auth/bootstrap`.
  - `p_location_name text` - required - `location_name` from `POST /auth/bootstrap`.
  - `p_location_address text` - required - `location_address` from `POST /auth/bootstrap`.
- Outputs:
  - Table columns `organization_id uuid`, `location_id uuid`, `user_id uuid`.
- Required fields: all inputs.
- Optional fields: none.
- Validation rules:
  - `p_auth_user_id` must not be null.
  - `p_email` must not be null or blank.
  - `p_organization_name` must not be null or blank.
  - `p_location_name` must not be null or blank.
  - `p_location_address` must not be null or blank.
- Defaults:
  - Inserted `app_users.email` is `lower(btrim(p_email))`.
  - Inserted `app_users.role` is `'owner'`.
  - Inserted organization name, location name, and location address are trimmed with `btrim`.
  - `notification_settings` uses existing table defaults.
- Status codes or result states:
  - New user id: inserts one `organizations` row, one `locations` row, one `app_users` row, and one `notification_settings` row.
  - Existing user id: returns existing `app_users.id`, existing `app_users.organization_id`, and the first active location id ordered by `locations.created_at ASC`; does not duplicate the organization.
- Error shapes:
  - PostgreSQL exception text from explicit `RAISE EXCEPTION` statements.
- Example input:
  ```sql
  SELECT *
  FROM bootstrap_first_owner_user(
      '00000000-0000-0000-0000-000000000000',
      'owner@example.com',
      'Main Pharmacy',
      'Main Pharmacy - Bank',
      '100 Bank St, Ottawa, ON'
  );
  ```
- Example output:
  ```text
  organization_id                       location_id                           user_id
  11111111-1111-1111-1111-111111111111  22222222-2222-2222-2222-222222222222  00000000-0000-0000-0000-000000000000
  ```

### `CurrentUserService.requireCurrentUser()`
- Name: `CurrentUserService.requireCurrentUser()`.
- Type: Spring service method.
- Purpose: Return the current typed authenticated user without repeated JWT claim parsing.
- Owner: Spring Boot backend.
- Inputs: current `SecurityContextHolder` authentication.
- Outputs: `AuthenticatedUserPrincipal`.
- Required fields: `SecurityContextHolder.getContext().getAuthentication()` must be an `AppUserAuthenticationToken`.
- Optional fields: none.
- Validation rules:
  - Throws `AuthenticationCredentialsNotFoundException` if no typed app user authentication exists.
- Defaults: none.
- Status codes or result states:
  - HTTP callers receive `401` with `{"error":"AUTHENTICATION_REQUIRED"}` through `AuthExceptionHandler`.
- Error shapes:
  - `{"error":"AUTHENTICATION_REQUIRED"}` when used from controller flow.
- Example input:
  ```java
  AuthenticatedUserPrincipal currentUser = currentUserService.requireCurrentUser();
  ```
- Example output:
  ```java
  currentUser.organizationId()
  ```

### Configuration Contract
- Name: Supabase JWT and datasource configuration.
- Type: Spring configuration/environment contract.
- Purpose: Connect Spring Boot to hosted Supabase Postgres and Supabase JWKS.
- Owner: Spring Boot backend.
- Inputs:
  - `SPRING_PROFILES_ACTIVE`
  - `DATABASE_URL`
  - `DATABASE_USERNAME`
  - `DATABASE_PASSWORD`
  - `SUPABASE_JWKS_URI`
  - `SUPABASE_JWT_ISSUER`
  - `SUPABASE_JWT_AUDIENCE`
- Outputs:
  - Configured datasource.
  - Configured Flyway migration runner.
  - Configured JWT decoder.
- Required fields:
  - For hosted Supabase prod: all listed environment variables except `SUPABASE_JWT_AUDIENCE` can fall back to default `authenticated` but should still be set explicitly.
- Optional fields:
  - `SUPABASE_JWT_ISSUER` is optional in code; issuer validation is skipped if blank.
- Validation rules:
  - `DATABASE_URL` must be a JDBC URL starting with `jdbc:postgresql://`.
  - Supabase pooler URLs should include `prepareThreshold=0`, and the prod datasource also sets Hikari data source property `prepareThreshold: 0`.
- Defaults:
  - Local datasource: `jdbc:postgresql://localhost:5432/pharmaforecast`.
  - Local username/password: `pharmaforecast`/`pharmaforecast`.
  - Local JWKS fallback: `http://localhost:54321/auth/v1/.well-known/jwks.json`.
  - JWT audience default: `authenticated`.
- Status codes or result states: not an HTTP interface.
- Error shapes:
  - Missing or invalid datasource config fails application startup.
  - Invalid JWKS URI fails JWT decoder use/startup depending on code path.
- Example input:
  ```sh
  SPRING_PROFILES_ACTIVE=prod
  DATABASE_URL=jdbc:postgresql://aws-1-ca-central-1.pooler.supabase.com:6543/postgres?sslmode=require&prepareThreshold=0
  DATABASE_USERNAME=postgres.ebrxagoygjtnpzlnxtmr
  DATABASE_PASSWORD=<database-password>
  SUPABASE_JWKS_URI=https://ebrxagoygjtnpzlnxtmr.supabase.co/auth/v1/.well-known/jwks.json
  SUPABASE_JWT_ISSUER=https://ebrxagoygjtnpzlnxtmr.supabase.co/auth/v1
  SUPABASE_JWT_AUDIENCE=authenticated
  ```
- Example output:
  ```text
  Flyway validates/applies migrations, JPA initializes, and Spring Security validates Supabase access tokens.
  ```

## 4. Data Contract

### `AuthenticatedUserPrincipal`
- Exact name: `ca.pharmaforecast.backend.auth.AuthenticatedUserPrincipal`.
- Type: Java record implementing `java.security.Principal`.
- Fields:
  - `id` - `UUID` - required - Supabase Auth user id mirrored as `app_users.id`.
  - `email` - `String` - required - email from `app_users.email`.
  - `organizationId` - `UUID` - required - tenant id from `app_users.organization_id`.
  - `role` - `UserRole` - required - app role from `app_users.role`.
- Allowed values:
  - `role`: existing `UserRole` enum values; this feature uses `owner`.
- Defaults: none.
- Validation constraints: constructed only after matching `app_users` row is found.
- Migration notes: no database migration for this Java record.
- Backward compatibility notes: new principal replaces direct use of raw `JwtAuthenticationToken` for protected app endpoints after `SupabaseUserContextFilter`.

### `AppUserAuthenticationToken`
- Exact name: `ca.pharmaforecast.backend.auth.AppUserAuthenticationToken`.
- Type: Java class extending `AbstractAuthenticationToken`.
- Fields:
  - `principal` - `AuthenticatedUserPrincipal` - required.
  - `jwt` - `Jwt` - required original Supabase JWT.
  - `authorities` - collection containing `ROLE_` plus `UserRole.name()`.
- Allowed values:
  - With current role, authority is `ROLE_owner`.
- Defaults: authenticated token is created by constructor.
- Validation constraints: created only after validated JWT and app user lookup.
- Migration notes: none.
- Backward compatibility notes: downstream code should use `CurrentUserService`, not cast this token directly unless necessary.

### `AuthController.CurrentUserResponse`
- Exact name: `ca.pharmaforecast.backend.auth.AuthController.CurrentUserResponse`.
- Type: Java record serialized to JSON.
- Fields:
  - `id` - `UUID` - required - JSON `id`.
  - `email` - `String` - required - JSON `email`.
  - `role` - `UserRole` - required - JSON `role`.
  - `organizationId` - `UUID` - required - JSON `organization_id`.
  - `locations` - `List<LocationResponse>` - required - JSON `locations`.
- Allowed values:
  - `role`: currently `owner` for bootstrap-created users.
- Defaults:
  - `locations` can be `[]`.
- Validation constraints: produced after `CurrentUserService.requireCurrentUser()`.
- Migration notes: none.
- Backward compatibility notes: JSON uses snake_case for `organization_id`.

### `AuthController.LocationResponse`
- Exact name: `ca.pharmaforecast.backend.auth.AuthController.LocationResponse`.
- Type: Java record serialized to JSON.
- Fields:
  - `id` - `UUID` - required.
  - `name` - `String` - required.
  - `address` - `String` - required.
- Allowed values: no enum values.
- Defaults: none.
- Validation constraints: values come from active `locations` rows.
- Migration notes: none.
- Backward compatibility notes: no compatibility concerns.

### `AuthController.BootstrapRequest`
- Exact name: `ca.pharmaforecast.backend.auth.AuthController.BootstrapRequest`.
- Type: Java record deserialized from JSON.
- Fields:
  - `organizationName` - `String` - required - JSON `organization_name` - `@NotBlank`.
  - `locationName` - `String` - required - JSON `location_name` - `@NotBlank`.
  - `locationAddress` - `String` - required - JSON `location_address` - `@NotBlank`.
- Allowed values: any nonblank strings.
- Defaults: none.
- Validation constraints:
  - `organization_name` must be present and nonblank.
  - `location_name` must be present and nonblank.
  - `location_address` must be present and nonblank.
- Migration notes: values are passed to `bootstrap_first_owner_user`.
- Backward compatibility notes: request body intentionally does not accept `role`, `organization_id`, `user_id`, or `email`.

### `AuthController.BootstrapResponse`
- Exact name: `ca.pharmaforecast.backend.auth.AuthController.BootstrapResponse`.
- Type: Java record serialized to JSON.
- Fields:
  - `organizationId` - `UUID` - required - JSON `organization_id`.
  - `locationId` - `UUID` - required - JSON `location_id`.
  - `userId` - `UUID` - required - JSON `user_id`.
- Allowed values: UUIDs returned by `bootstrap_first_owner_user`.
- Defaults: none.
- Validation constraints: none at DTO layer.
- Migration notes: none.
- Backward compatibility notes: JSON uses snake_case.

### `AuthBootstrapService.BootstrapCommand`
- Exact name: `ca.pharmaforecast.backend.auth.AuthBootstrapService.BootstrapCommand`.
- Type: Java record.
- Fields:
  - `authUserId` - `UUID` - required.
  - `email` - `String` - required.
  - `organizationName` - `String` - required.
  - `locationName` - `String` - required.
  - `locationAddress` - `String` - required.
- Allowed values: validated by controller and SQL function.
- Defaults: none.
- Validation constraints: no annotations; callers must pass validated data.
- Migration notes: maps directly to `bootstrap_first_owner_user` parameters.
- Backward compatibility notes: service contract is internal to backend.

### `AuthBootstrapService.BootstrapResult`
- Exact name: `ca.pharmaforecast.backend.auth.AuthBootstrapService.BootstrapResult`.
- Type: Java record.
- Fields:
  - `organizationId` - `UUID` - required.
  - `locationId` - `UUID` - required.
  - `userId` - `UUID` - required.
- Allowed values: UUIDs returned from PostgreSQL function.
- Defaults: none.
- Validation constraints: none at record layer.
- Migration notes: maps directly to `bootstrap_first_owner_user` return table.
- Backward compatibility notes: service contract is internal to backend.

### `bootstrap_first_owner_user`
- Exact name: `bootstrap_first_owner_user`.
- Type: PostgreSQL function from `/Users/adamsaleh/Downloads/pharmacast-backend/src/main/resources/db/migration/V2__auth_bootstrap.sql`.
- Fields/parameters:
  - `p_auth_user_id uuid` - required.
  - `p_email text` - required.
  - `p_organization_name text` - required.
  - `p_location_name text` - required.
  - `p_location_address text` - required.
- Return fields:
  - `organization_id uuid` - required.
  - `location_id uuid` - required.
  - `user_id uuid` - required.
- Allowed values:
  - `app_users.role` inserted value: `'owner'`.
- Defaults:
  - Database defaults for UUID primary keys and timestamps are inherited from `V1__foundation_schema.sql`.
  - `notification_settings` defaults are inherited from `V1__foundation_schema.sql`.
- Validation constraints:
  - Explicit `RAISE EXCEPTION` when any parameter is null or blank where applicable.
- Migration notes:
  - Migration file: `V2__auth_bootstrap.sql`.
  - Function is `SECURITY DEFINER`.
  - Function sets `search_path = public`.
- Backward compatibility notes:
  - Existing user id path is idempotent and does not duplicate organizations.

### Existing tables touched by bootstrap
- Exact names:
  - `organizations`
  - `locations`
  - `app_users`
  - `notification_settings`
- Fields used:
  - `organizations.name`.
  - `locations.organization_id`, `locations.name`, `locations.address`, `locations.deactivated_at`, `locations.created_at`.
  - `app_users.id`, `app_users.organization_id`, `app_users.email`, `app_users.role`.
  - `notification_settings.organization_id`.
- Field types: owned by `V1__foundation_schema.sql`.
- Required vs optional: owned by `V1__foundation_schema.sql`.
- Allowed values:
  - `app_users.role = 'owner'` for bootstrap-created users.
- Defaults: owned by `V1__foundation_schema.sql`.
- Validation constraints:
  - Bootstrap inserts only one organization, one location, one app user, and one notification settings row for new users.
- Migration notes:
  - `V2__auth_bootstrap.sql` depends on these tables already existing.
- Backward compatibility notes:
  - Does not rename or remove existing foundation tables.

### `.env.example`
- Exact name: `/Users/adamsaleh/Downloads/pharmacast-backend/.env.example`.
- Type: environment template file.
- Fields:
  - `SPRING_PROFILES_ACTIVE`
  - `DATABASE_URL`
  - `DATABASE_USERNAME`
  - `DATABASE_PASSWORD`
  - `SUPABASE_JWKS_URI`
  - `SUPABASE_JWT_ISSUER`
  - `SUPABASE_JWT_AUDIENCE`
- Required vs optional:
  - All are required for the recommended hosted Supabase setup, except `SUPABASE_JWT_AUDIENCE` has code default `authenticated`.
- Allowed values:
  - `SPRING_PROFILES_ACTIVE=prod`.
  - `DATABASE_URL` must be a JDBC PostgreSQL URL.
- Defaults:
  - Template uses placeholders and contains no real secrets.
- Validation constraints:
  - Real `.env` must not be committed.
- Migration notes: none.
- Backward compatibility notes: `.gitignore` allows `.env.example` while ignoring `.env`.

## 5. Integration Contract
- Upstream dependencies:
  - Supabase Auth issues JWTs.
  - Supabase JWKS endpoint exposes signing keys.
  - Hosted Supabase PostgreSQL stores app tables.
  - Spring Security OAuth2 Resource Server validates bearer tokens.
  - Spring Data JPA reads `app_users` and `locations`.
  - Flyway applies `V1__foundation_schema.sql` and `V2__auth_bootstrap.sql`.
- Downstream dependencies:
  - Frontend must call Supabase Auth for signup/signin/session.
  - Frontend must send Supabase access token as `Authorization: Bearer <token>` to Spring Boot.
  - Frontend must call `POST /auth/bootstrap` after first signup before relying on `GET /auth/me`.
- Services called:
  - Spring Security calls Supabase JWKS URI through `NimbusJwtDecoder`.
  - `JdbcAuthBootstrapService` calls PostgreSQL function `bootstrap_first_owner_user`.
- Endpoints hit:
  - Backend validates JWTs using `SUPABASE_JWKS_URI`.
  - Backend does not call Supabase Auth Admin endpoints.
- Events consumed: NOT IMPLEMENTED.
- Events published: NOT IMPLEMENTED.
- Files read or written:
  - Reads runtime config from environment variables and `application.yml`.
  - Flyway reads migrations from `classpath:db/migration`.
  - Documentation reads/writes are not runtime behavior.
- Environment assumptions:
  - Java 21 target project; local run in this environment used Java 25 but Maven compiles with release 21.
  - Hosted Supabase pooler may use host `aws-1-ca-central-1.pooler.supabase.com`, port `6543`, database `postgres`, and username shaped like `postgres.<project-ref>`.
  - Supabase project ref used during setup: `ebrxagoygjtnpzlnxtmr`.
  - Real database password must be supplied through `.env` or deployment secrets.
  - Supabase Dashboard remains available for visual inspection of Auth users and tables.
- Auth assumptions:
  - Supabase Auth is source of truth for identity.
  - Spring Boot is source of truth for app role and organization context after lookup in `app_users`.
  - JWT `sub` maps to `app_users.id`.
  - JWT `email` must match `app_users.email`.
- Retry behavior:
  - `POST /auth/bootstrap` is idempotent for an existing `app_users.id`; it returns existing tenant data instead of creating a duplicate tenant.
  - `GET /auth/me` has no explicit retry logic.
  - JWT decoder/JWKS retrieval uses Spring/Nimbus defaults; no custom retry behavior implemented.
- Timeout behavior:
  - No custom HTTP timeout for JWKS retrieval is implemented.
  - No custom JDBC query timeout for bootstrap is implemented.
- Fallback behavior:
  - `SUPABASE_JWT_ISSUER` blank skips issuer validation.
  - Missing `SUPABASE_JWKS_URI` falls back to local Supabase JWKS URL.
  - Missing `SUPABASE_JWT_AUDIENCE` falls back to `authenticated`.
  - Missing prod datasource values fails startup.
- Idempotency behavior:
  - `bootstrap_first_owner_user` is idempotent by `app_users.id`.
  - `POST /auth/logout` is stateless and idempotent from the backend perspective.
- Docker integration:
  - Docker is not required for hosted Supabase runtime.
  - Docker is required for Testcontainers-backed tests to execute instead of skip.

## 6. Usage Instructions for Other Engineers
- To protect a new backend endpoint, do not add it to the public matcher list in `SecurityConfig`; all non-public endpoints already require bearer token authentication.
- To access the current user in application code, inject `CurrentUserService` and call `requireCurrentUser()`.
- Do not manually parse JWT claims in controllers or services for `id`, `email`, `organization_id`, or `role`.
- Use `AuthenticatedUserPrincipal.id()` for current user id.
- Use `AuthenticatedUserPrincipal.email()` for current user email.
- Use `AuthenticatedUserPrincipal.organizationId()` for tenant scope.
- Use `AuthenticatedUserPrincipal.role()` for role-aware behavior.
- For frontend signup/onboarding, collect exactly:
  - `organization_name`
  - `location_name`
  - `location_address`
- Frontend signup flow must:
  - Sign up/sign in with Supabase Auth.
  - Retrieve Supabase `access_token`.
  - Call `POST /auth/bootstrap` with bearer token and onboarding body.
  - Call `GET /auth/me` after bootstrap.
- Handle loading states:
  - Signup/signin pending in frontend Supabase client.
  - Backend bootstrap pending.
  - Current-user request pending.
- Handle empty states:
  - `GET /auth/me` can return `"locations": []`.
- Handle success states:
  - `POST /auth/bootstrap` returns ids needed to proceed.
  - `GET /auth/me` returns authenticated app user context.
- Handle failure states:
  - `401 AUTHENTICATION_REQUIRED`: user is not signed in or request lacks usable JWT.
  - `403 USER_PROFILE_NOT_BOOTSTRAPPED`: user signed in through Supabase but has not completed backend bootstrap.
  - `400` validation errors from bootstrap body: onboarding form missing required fields.
- Finalized:
  - Route names.
  - Request/response JSON field names.
  - Principal shape.
  - Bootstrap SQL function name and parameters.
  - Hosted Supabase env var names.
- Provisional:
  - Role matrix beyond `owner`.
  - RLS policy coverage for all future domain operations.
  - Production deployment secret management on Fly.io.
- MOCKED:
  - `AuthTestRepositoryConfig` provides mocked/test-double repositories and bootstrap service for `AuthEndpointTest`.
- Must not be changed without coordination:
  - `JWT sub -> app_users.id` mapping.
  - `JWT email -> app_users.email` match requirement.
  - `organization_id` and `role` coming from database, not request body or user metadata.
  - Stateless logout behavior.
  - `prepareThreshold=0` for Supabase pooler compatibility.

## 7. Security and Authorization Notes
- Auth requirements:
  - `/health`, `/actuator/health`, `/actuator/health/**`, and `/auth/**` are public at Spring Security matcher level.
  - Protected endpoints outside those matchers require bearer token authentication.
  - `GET /auth/me` is public at matcher level but requires `CurrentUserService.requireCurrentUser()` and therefore returns `401` without app authentication.
  - `POST /auth/bootstrap` is public at matcher level but requires `JwtAuthenticationToken`.
- Permission rules:
  - No broad role matrix was implemented.
  - Bootstrap-created users receive role `owner`.
  - `AppUserAuthenticationToken` carries authority `ROLE_` plus `UserRole.name()`, currently `ROLE_owner`.
- Tenancy rules:
  - `organization_id` comes from `app_users.organization_id`.
  - `GET /auth/me` locations are filtered by current user's `organizationId`.
  - Organization/location ownership validation for future domain endpoints remains required and is NOT IMPLEMENTED by this feature.
- Role checks:
  - No `@PreAuthorize` or route-level role checks were added.
  - Role is carried for future extensibility.
- Data isolation:
  - Backend enforces current-user tenant context for `/auth/me`.
  - Supabase RLS should still isolate tenant data, but Spring Boot must remain enforcement layer.
- Sensitive fields:
  - Bearer tokens must not be logged.
  - Database password must not be committed.
  - Supabase service role key must never be used in frontend code.
  - `patient_id` is not touched by this feature.
- Sanitization:
  - Bootstrap trims input in SQL before insert.
  - Auth endpoints do not send patient-level data to LLM services.
- Forbidden fields:
  - `POST /auth/bootstrap` must not accept `role`.
  - `POST /auth/bootstrap` must not accept `organization_id`.
  - `POST /auth/bootstrap` must not accept `user_id`.
  - `POST /auth/bootstrap` must not trust Supabase user metadata for tenant or role.
- Logging restrictions:
  - Do not log bearer tokens.
  - Do not log database passwords.
  - Do not log patient identifiers.
- Compliance concerns:
  - Hosted Supabase should be in Canada region where available.
  - Product remains inventory decision support, not medical or prescribing decision support.

## 8. Environment and Configuration
- `SPRING_PROFILES_ACTIVE`
  - Purpose: selects Spring profile.
  - Required or optional: required for hosted Supabase setup.
  - Default behavior if missing: application uses default `local` profile.
  - Dev vs prod notes: set `SPRING_PROFILES_ACTIVE=prod` for hosted Supabase.
- `DATABASE_URL`
  - Purpose: JDBC URL for PostgreSQL.
  - Required or optional: required in `prod`.
  - Default behavior if missing: app fails datasource configuration in `prod`.
  - Dev vs prod notes: hosted Supabase pooler format is `jdbc:postgresql://<host>:<port>/<database>?sslmode=require&prepareThreshold=0`.
- `DATABASE_USERNAME`
  - Purpose: PostgreSQL username.
  - Required or optional: required in `prod`.
  - Default behavior if missing: app fails database authentication.
  - Dev vs prod notes: Supabase pooler username often looks like `postgres.<project-ref>`; direct connection username often looks like `postgres`.
- `DATABASE_PASSWORD`
  - Purpose: PostgreSQL password.
  - Required or optional: required in `prod`.
  - Default behavior if missing: app fails database authentication.
  - Dev vs prod notes: keep in local `.env` or deployment secrets only; never commit.
- `SUPABASE_JWKS_URI`
  - Purpose: JWKS endpoint for Supabase JWT signature validation.
  - Required or optional: optional by code default, required for real hosted Supabase correctness.
  - Default behavior if missing: falls back to `http://localhost:54321/auth/v1/.well-known/jwks.json`.
  - Dev vs prod notes: hosted format is `https://<project-ref>.supabase.co/auth/v1/.well-known/jwks.json`.
- `SUPABASE_JWT_ISSUER`
  - Purpose: optional issuer validator for Supabase JWTs.
  - Required or optional: optional in code, recommended for hosted Supabase.
  - Default behavior if missing: issuer validation is skipped.
  - Dev vs prod notes: hosted format is `https://<project-ref>.supabase.co/auth/v1`.
- `SUPABASE_JWT_AUDIENCE`
  - Purpose: accepted JWT audience.
  - Required or optional: optional.
  - Default behavior if missing: `authenticated`.
  - Dev vs prod notes: keep `authenticated` unless Supabase project JWT audience config changes.
- `pharmaforecast.security.supabase.jwk-set-uri`
  - Purpose: Spring property bound from `SUPABASE_JWKS_URI`.
  - Required or optional: optional due to local default.
  - Default behavior if missing: local Supabase JWKS URL.
  - Dev vs prod notes: set through env var for hosted Supabase.
- `pharmaforecast.security.supabase.issuer`
  - Purpose: Spring property bound from `SUPABASE_JWT_ISSUER`.
  - Required or optional: optional.
  - Default behavior if missing: issuer validator is not added.
  - Dev vs prod notes: set for hosted Supabase.
- `pharmaforecast.security.supabase.audiences`
  - Purpose: accepted JWT audiences.
  - Required or optional: optional.
  - Default behavior if missing: `authenticated`.
  - Dev vs prod notes: set from `SUPABASE_JWT_AUDIENCE`.
- `spring.datasource.hikari.data-source-properties.prepareThreshold`
  - Purpose: disables PostgreSQL server-side prepared statements for Supabase pooler compatibility.
  - Required or optional: required for stable Supabase pooler behavior on port `6543`.
  - Default behavior if missing: PostgreSQL JDBC driver may create server-side prepared statements and fail through the pooler with `prepared statement "S_1" already exists`.
  - Dev vs prod notes: prod profile sets `prepareThreshold: 0`; `.env.example` also includes `prepareThreshold=0` in `DATABASE_URL`.
- `spring.flyway.baseline-on-migrate`
  - Purpose: lets Flyway initialize a schema history table in hosted Supabase's non-empty `public` schema.
  - Required or optional: required for hosted Supabase setup when schema is non-empty without Flyway history.
  - Default behavior if missing: Flyway can fail with `Found non-empty schema(s) "public" but no schema history table`.
  - Dev vs prod notes: prod profile sets `true`.
- `spring.flyway.baseline-version`
  - Purpose: baseline version used with `baseline-on-migrate`.
  - Required or optional: required with current hosted Supabase setup.
  - Default behavior if missing: Flyway default baseline version may prevent intended versioned migrations from running.
  - Dev vs prod notes: prod profile sets `0`.
- `spring.flyway.locations`
  - Purpose: migration path selection.
  - Required or optional: required.
  - Default behavior if missing: base value is `classpath:db/migration`.
  - Dev vs prod notes: local includes `classpath:db/dev-migration`; prod uses only `classpath:db/migration`.
- `.env`
  - Purpose: local uncommitted environment file for runtime secrets.
  - Required or optional: required for beginner local hosted Supabase run unless env vars are exported another way.
  - Default behavior if missing: shell will not populate required prod env vars.
  - Dev vs prod notes: ignored by `.gitignore`.
- `.env.example`
  - Purpose: committed non-secret template.
  - Required or optional: optional at runtime.
  - Default behavior if missing: no runtime impact.
  - Dev vs prod notes: use as a template only; do not put real password in `.env.example`.

## 9. Testing and Verification
- Tests added or updated:
  - `AuthEndpointTest.protectedRoutesRequireBearerToken`.
  - `AuthEndpointTest.currentUserRequiresAuthenticatedContext`.
  - `AuthEndpointTest.currentUserReturnsDatabaseBackedPrincipalAndActiveLocations`.
  - `AuthEndpointTest.validJwtWithoutAppUserReturnsBootstrapRequired`.
  - `AuthEndpointTest.logoutIsStatelessAcknowledgement`.
  - `AuthEndpointTest.bootstrapCreatesFirstOwnerTenantShapeFromValidJwtAndMetadata`.
  - `AuthEndpointTest.bootstrapRequiresJwt`.
  - `HealthEndpointTest.healthEndpointReturnsStatusAndTimestamp`.
  - `FlywayMigrationTest` includes Docker/Testcontainers migration checks and bootstrap function checks.
- What was manually verified:
  - Hosted Supabase connection reached PostgreSQL at `jdbc:postgresql://aws-1-ca-central-1.pooler.supabase.com:6543/postgres`.
  - Flyway validated migrations against hosted Supabase.
  - Hosted Supabase schema reported current version `2`.
  - Hosted Supabase schema reported up to date.
  - JPA `EntityManagerFactory` initialized against hosted Supabase before the final bootstrap bean wiring fix.
  - Final hosted Supabase app startup after `AuthBootstrapConfiguration` was added is not yet confirmed in chat.
- How to run the tests:
  ```sh
  cd /Users/adamsaleh/Downloads/pharmacast-backend
  mvn test
  ```
- Latest verification output:
  ```text
  Tests run: 11, Failures: 0, Errors: 0, Skipped: 3
  BUILD SUCCESS
  ```
- How to locally validate the feature with hosted Supabase:
  ```sh
  cd /Users/adamsaleh/Downloads/pharmacast-backend
  set -a
  source .env
  set +a
  mvn clean spring-boot:run
  ```
  Then in another terminal:
  ```sh
  curl http://localhost:8080/health
  ```
  Then use `docs/setup/supabase-hosted.md` to create/sign in a Supabase user, call `POST /auth/bootstrap`, and call `GET /auth/me`.
- Known gaps in test coverage:
  - Hosted Supabase startup is manually validated through logs, not automated.
  - JWKS retrieval from the real hosted Supabase URL is not covered by automated tests.
  - Supabase Auth signup/signin flow is not automated.
  - RLS policies are not covered by these auth endpoint tests.
  - Docker/Testcontainers tests are skipped when Docker is unavailable.

## 10. Known Limitations and TODOs
- Supabase Admin API logout/token revocation is NOT IMPLEMENTED.
- Frontend signup/onboarding UI is NOT IMPLEMENTED.
- Full role matrix beyond `owner` is NOT IMPLEMENTED.
- Per-domain organization/location authorization beyond `/auth/me` context is NOT IMPLEMENTED.
- RLS policy implementation is not part of this authentication feature contract; existing `src/main/resources/db/supabase/rls_policies.sql` remains separate.
- `POST /auth/bootstrap` currently returns a Spring default validation body for `@NotBlank` failures; no custom validation error shape was implemented.
- `POST /auth/bootstrap` database exceptions use Spring default error handling; no custom SQL error mapper was implemented.
- JWKS retrieval uses Nimbus/Spring defaults; no custom cache, timeout, or retry policy was implemented.
- The Flyway warning `PostgreSQL 17.6 is newer than this version of Flyway` was observed against hosted Supabase; migrations still validated successfully.
- Supabase CLI config exists in `supabase/`, but hosted Supabase Dashboard setup is the selected path; local Supabase Docker stack was not started.
- Docker is required for Testcontainers tests to execute locally; without Docker they skip.
- If a user exists in `app_users` but has no active location, `bootstrap_first_owner_user` existing-user path returns no row; this edge should be handled if users can exist without active locations.
- The project currently has a real `.env` file in the workspace; it is ignored by git and must not be committed or pasted into logs.

## 11. Source of Truth Snapshot
- Final route names:
  - `GET /auth/me`
  - `POST /auth/bootstrap`
  - `POST /auth/logout`
  - `GET /health`
- Final public Spring Security matchers:
  - `/health`
  - `/actuator/health`
  - `/actuator/health/**`
  - `/auth/**`
- Final DTO/model names:
  - `AuthenticatedUserPrincipal`
  - `AppUserAuthenticationToken`
  - `AuthController.CurrentUserResponse`
  - `AuthController.LocationResponse`
  - `AuthController.BootstrapRequest`
  - `AuthController.BootstrapResponse`
  - `AuthBootstrapService.BootstrapCommand`
  - `AuthBootstrapService.BootstrapResult`
- Final service/config names:
  - `CurrentUserService`
  - `SupabaseUserContextFilter`
  - `AuthBootstrapService`
  - `AuthBootstrapConfiguration`
  - `JdbcAuthBootstrapService`
  - `JwtSecurityConfig`
  - `SupabaseJwtProperties`
  - `SecurityConfig`
- Final SQL function:
  - `bootstrap_first_owner_user(uuid, text, text, text, text)`
- Final enum/status values:
  - `UserRole.owner`
  - `ROLE_owner`
  - JWT audience `authenticated`
- Final error codes:
  - `AUTHENTICATION_REQUIRED`
  - `JWT_SUBJECT_MISSING`
  - `JWT_SUBJECT_INVALID`
  - `JWT_EMAIL_MISSING`
  - `USER_PROFILE_NOT_BOOTSTRAPPED`
- Final event names: NOT IMPLEMENTED.
- Final key file paths:
  - `/Users/adamsaleh/Downloads/pharmacast-backend/src/main/java/ca/pharmaforecast/backend/config/SecurityConfig.java`
  - `/Users/adamsaleh/Downloads/pharmacast-backend/src/main/java/ca/pharmaforecast/backend/config/JwtSecurityConfig.java`
  - `/Users/adamsaleh/Downloads/pharmacast-backend/src/main/java/ca/pharmaforecast/backend/auth/AuthController.java`
  - `/Users/adamsaleh/Downloads/pharmacast-backend/src/main/java/ca/pharmaforecast/backend/auth/SupabaseUserContextFilter.java`
  - `/Users/adamsaleh/Downloads/pharmacast-backend/src/main/java/ca/pharmaforecast/backend/auth/CurrentUserService.java`
  - `/Users/adamsaleh/Downloads/pharmacast-backend/src/main/java/ca/pharmaforecast/backend/auth/AuthBootstrapConfiguration.java`
  - `/Users/adamsaleh/Downloads/pharmacast-backend/src/main/java/ca/pharmaforecast/backend/auth/JdbcAuthBootstrapService.java`
  - `/Users/adamsaleh/Downloads/pharmacast-backend/src/main/resources/db/migration/V2__auth_bootstrap.sql`
  - `/Users/adamsaleh/Downloads/pharmacast-backend/src/main/resources/application.yml`
  - `/Users/adamsaleh/Downloads/pharmacast-backend/docs/setup/supabase-hosted.md`
  - `/Users/adamsaleh/Downloads/pharmacast-backend/.env.example`
- Breaking changes from previous version:
  - All non-public endpoints now require bearer-token authentication.
  - Authenticated app code should rely on `AuthenticatedUserPrincipal` through `CurrentUserService`, not raw JWT claim parsing.
  - Bootstrap role/tenant data is controlled by backend/database, not frontend metadata.

## 12. Copy-Paste Handoff for the Next Engineer
The Supabase authentication feature is implemented in the Spring Boot backend. Spring Security validates Supabase JWTs through JWKS, then `SupabaseUserContextFilter` maps the validated JWT to `app_users` and installs an `AuthenticatedUserPrincipal`. Use `CurrentUserService.requireCurrentUser()` for app code that needs `id`, `email`, `organizationId`, or `role`.

It is safe to depend on `GET /auth/me`, `POST /auth/bootstrap`, `POST /auth/logout`, `AuthBootstrapService`, `AuthenticatedUserPrincipal`, and the SQL function `bootstrap_first_owner_user(uuid, text, text, text, text)`. The request/response JSON field names in this contract are final for the current feature.

What remains to be built: frontend signup/onboarding, frontend Supabase session handling, domain-level organization/location authorization, RLS policy rollout/verification, production deployment secrets, and any role matrix beyond `owner`. Watch for these traps: hosted Supabase pooler needs `prepareThreshold=0`, Flyway needs the prod baseline settings for Supabase's non-empty `public` schema, and Spring Boot must never trust frontend-supplied `role` or `organization_id`.
