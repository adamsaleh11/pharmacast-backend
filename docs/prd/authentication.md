# Authentication PRD

## Problem Statement

PharmaForecast needs a production-ready backend authentication boundary before tenant data, forecasting, uploads, purchase orders, and LLM-backed workflows can be safely exposed. Pharmacists will sign in through Supabase Auth, but Spring Boot must remain the orchestration and enforcement layer for API access, tenant membership, and application user context. The current backend only exposes health and denies all other routes, so authenticated users cannot retrieve their profile context or safely enter the application.

## Solution

Implement Supabase JWT validation in Spring Security using Supabase JWKS, then resolve every authenticated request into a typed PharmaForecast user context. The backend will expose `/auth/me` so the frontend can load the current user, role, organization, and active locations after Supabase sign-in. The backend will expose `/auth/logout` as a stateless Supabase-compatible acknowledgement while the frontend remains responsible for calling Supabase sign-out and discarding local session state.

First-time signup bootstrap will be supported through a DB-side function that creates an organization, first location, owner user row, and default notification settings from trusted signup context. Supabase remains the JWT issuer and identity source of truth; PharmaForecast database rows remain the authorization and tenant profile source of truth.

## User Stories

1. As a pharmacist, I want to sign in with Supabase Auth, so that I can use PharmaForecast without the backend issuing its own JWT.
2. As a signed-in pharmacist, I want the backend to validate my bearer token, so that protected pharmacy data is not exposed to unauthenticated callers.
3. As a signed-in pharmacist, I want `/auth/me` to return my id, email, role, organization id, and locations, so that the frontend can initialize the app shell.
4. As a first-time owner, I want signup to create my organization, first location, and owner profile, so that I can enter the product after confirming my Supabase account.
5. As a frontend engineer, I want a clear bootstrap metadata contract, so that signup can collect and send the required organization and location details.
6. As a backend engineer, I want a reusable current-user service, so that future controllers do not repeatedly parse JWT claims.
7. As a security reviewer, I want role and organization membership loaded from the database, so that stale or manipulated JWT metadata cannot authorize tenant access.
8. As a pharmacist, I want inactive locations omitted from my current-user response, so that I only see usable locations in the app.
9. As a frontend engineer, I want a clear response when a valid Supabase user has not been bootstrapped, so that onboarding can recover cleanly.
10. As an operator, I want public health endpoints to stay unauthenticated, so that infrastructure health checks continue to work.
11. As a user, I want logout to have a documented contract, so that the frontend and backend do not imply server-side JWT revocation that Spring Boot cannot perform.
12. As a future backend engineer, I want the role model to remain extensible, so that owner/admin/pharmacist/staff rules can be added without rewriting authentication.

## Implementation Decisions

- Spring Security will be configured as an OAuth2 resource server using Supabase JWKS.
- Public endpoints are `/health`, actuator health routes, and `/auth/**`; routes outside those patterns require bearer authentication.
- `/auth/me` is under `/auth/**` but still requires a valid authenticated user context at the controller/service layer.
- JWT claims used directly by the backend are limited to identity fields: `sub` and `email`.
- `organization_id` and `role` are loaded from `app_users`, not trusted from JWT metadata.
- A typed authenticated principal will expose `id`, `email`, `organization_id`, and `role`.
- A current-user helper service will be the supported way for backend code to access authenticated user context.
- If a valid JWT has no matching `app_users` row, the backend returns `403` with `USER_PROFILE_NOT_BOOTSTRAPPED`.
- `/auth/me` returns only active locations.
- `/auth/logout` does not revoke Supabase JWTs. It returns a stateless success response and documents that the frontend must call Supabase sign-out.
- Signup bootstrap is represented as a DB-side callable function, not a Spring-issued JWT or role-claim mutation.
- Frontend signup metadata expected for bootstrap is `organization_name`, `location_name`, and `location_address`.

## Testing Decisions

- Test public behavior through HTTP endpoints and Spring Security filters rather than private methods.
- Verify `/health` remains public.
- Verify non-auth routes require authentication.
- Verify `/auth/me` returns the typed current user and active locations for a valid JWT and matching `app_users` row.
- Verify a valid JWT without an `app_users` row returns the bootstrap-required error.
- Verify `/auth/logout` returns the documented stateless success status.
- Verify Flyway migrations apply to PostgreSQL, including the bootstrap function.

## Out of Scope

- Spring Boot issuing JWTs.
- Full role matrix authorization beyond preserving the role value.
- Supabase Admin API token revocation.
- Frontend signup UI.
- Python forecasting or LLM service changes.
- Patient data handling beyond maintaining the existing rule that auth and LLM payloads must not include `patient_id`.

## Further Notes

- Supabase Auth is the identity source of truth.
- The PharmaForecast database is the application authorization source of truth.
- `app_users.id` must match Supabase `auth.uid()`.
- The bootstrap function is designed to work in plain PostgreSQL migration tests and in Supabase-hosted PostgreSQL.
- Future authorization checks should use the typed current-user principal and organization/location validation services rather than reading raw JWT claims.
