# PRD: Foundation, Schema, Backend Setup

## Problem Statement

Independent pharmacies need PharmaForecast to become a trustworthy operational system before any forecasting, alerting, purchase ordering, billing, or assistant workflows can be built. The backend currently lacks a durable Spring Boot foundation, tenant-aware PostgreSQL schema, migration discipline, and testable persistence contracts.

This matters now because the product has strict architectural and compliance boundaries: Spring Boot must orchestrate and enforce authorization, Supabase PostgreSQL must isolate tenant data, Python services must remain narrow compute/language services, and patient-level data must never leak into LLM contexts, logs, exports, or generated artifacts. A weak foundation would make later CSV ingestion, forecasts, alerts, chat, purchase orders, and billing risky to implement.

## Solution

Create a production-grade Spring Boot 3 backend foundation for PharmaForecast using Java 21, Maven, Flyway, Spring Data JPA, PostgreSQL, Spring Security, validation, actuator, and OAuth2 resource server dependencies. The foundation will define the domain schema for organizations, locations, application users, drugs, dispensing records, forecasts, thresholds, stock adjustments, purchase orders, notifications, CSV uploads, chat messages, and notification settings.

The backend will expose only a simple health endpoint in this foundation phase. It will not implement authentication logic, CSV ingestion, forecasting integration, LLM integration, notifications, billing, or business APIs yet. The value of this phase is a strict, migration-safe data model, clean package structure, integration-ready entity/repository layer, local development database setup, Supabase RLS SQL, and a TDD-ready test baseline.

## User Stories

1. As a backend developer, I want a Java 21 Spring Boot 3 Maven project, so that future PharmaForecast backend features start from a stable platform.
2. As a backend developer, I want package boundaries for configuration, common code, auth, organization, location, drug, dispensing, forecast, upload, LLM, chat, notification, purchase order, insights, and billing, so that future work has clear ownership.
3. As an operator, I want a health endpoint that returns an explicit status and timestamp, so that deployments can be checked without depending on business features.
4. As a backend developer, I want local and production configuration profiles, so that development can use local PostgreSQL while production can use managed Supabase PostgreSQL.
5. As a backend developer, I want Flyway-controlled schema migrations, so that database changes are versioned, repeatable, and safe to apply across environments.
6. As a pharmacist organization owner, I want each organization to have isolated locations, users, records, uploads, forecasts, purchase orders, notifications, and settings, so that tenant data cannot cross organizational boundaries.
7. As a multi-location pharmacy operator, I want locations to belong to an organization and support deactivation, so that closed or inactive stores can stop participating without destroying historical data.
8. As a pharmacist, I want drugs represented by DIN and catalog metadata, so that dispensing history and forecasts can reference stable drug identifiers.
9. As a pharmacist, I want dispensing records to preserve dispensed date, quantities, on-hand quantity, cost, and optional patient ID, so that demand forecasting can later operate on historical data while keeping patient data protected.
10. As a compliance reviewer, I want patient IDs stored only where required and excluded from LLM-facing structures, so that patient-level data is not sent to external language models.
11. As a backend developer, I want forecasts persisted by Spring Boot, so that Python forecasting remains numeric-only and does not own database writes.
12. As a pharmacist, I want forecast rows to store horizon, predicted quantity, confidence, days of supply, reorder status, Prophet interval values, average demand, reorder point, and data points used, so that later UI and alert workflows can explain inventory risk.
13. As a pharmacist, I want configurable drug thresholds per location and DIN, so that reorder risk can reflect local lead times and safety posture.
14. As a backend developer, I want stock adjustments stored separately from dispensing records, so that inventory corrections can be audited without corrupting historical demand.
15. As a pharmacist, I want purchase orders persisted with generated text and structured line items, so that future purchase order drafting can remain reviewable and auditable.
16. As a pharmacist, I want notifications persisted with type, payload, sent time, and read time, so that future alert and digest workflows can be built reliably.
17. As a pharmacist, I want CSV uploads tracked with filename, status, errors, row counts, drug counts, validation summary, and upload time, so that ingestion failures and history can be understood.
18. As a pharmacist, I want chat messages associated with a location and role, so that future assistant conversations can be scoped to the correct pharmacy context.
19. As an organization owner, I want notification settings at the organization level, so that digest and critical alert behavior can be configured once per tenant.
20. As a backend developer, I want repositories for every domain table, so that later services can build on typed persistence boundaries instead of ad hoc SQL.
21. As a backend developer, I want enums for stable domain concepts, so that roles, confidence, reorder status, purchase order status, CSV upload status, safety posture, and notification types are explicit.
22. As a database operator, I want indexes on common lookup and timeline queries, so that future forecast, upload, notification, chat, and dispensing views have practical query paths.
23. As a Supabase operator, I want RLS SQL for public drug reads and tenant-owned table access, so that Supabase policies are documented before frontend or direct Supabase reads are introduced.
24. As a backend developer, I want backend service-role access documented as compatible with RLS, so that Spring Boot jobs can orchestrate work while still enforcing authorization in application code.
25. As a new developer, I want dev seed data for one organization, one location, one owner user, sample drugs, and sample dispensing history, so that local setup can demonstrate the domain model immediately.
26. As a backend developer, I want baseline tests for health, context startup, and migrations, so that schema and configuration defects are caught before feature work builds on them.
27. As a product owner, I want this phase to exclude auth flows, forecasting calls, LLM calls, CSV processing, notification delivery, billing, and business endpoints, so that foundation work stays focused and verifiable.
28. As a backend developer, I want schema-level uniqueness rules for tenant-specific configuration, so that later services can rely on one threshold row per location and drug.
29. As a backend developer, I want chat persistence to be location-scoped without conversation grouping in v1 foundation, so that future assistant work can add conversations only when product behavior requires it.
30. As a compliance reviewer, I want patient identifiers treated as opaque pseudonymous source identifiers, so that the foundation does not imply storage of clinical identity details.

## Implementation Decisions

- The backend will use Maven.
- The Java base package will be `ca.pharmaforecast.backend`.
- The physical database table for application users will be `app_users`, not `users`, to avoid confusion with Supabase Auth, `auth.users`, and database roles. This satisfies the product domain concept of users while keeping the SQL surface unambiguous.
- PostgreSQL will generate UUID primary keys using `gen_random_uuid()`.
- PostgreSQL will own `created_at` and `updated_at` through defaults and an `updated_at` trigger.
- All persisted timestamps will use `timestamptz` and represent UTC.
- Domain enums will be stored as text columns with database `CHECK` constraints, not PostgreSQL enum types.
- Application user roles will be constrained to `owner`, `admin`, `pharmacist`, and `staff`.
- Forecast confidence will be constrained to `low`, `medium`, and `high`.
- Reorder status will be constrained to `ok`, `amber`, and `red`.
- Purchase order status will be constrained to `draft`, `reviewed`, `sent`, and `cancelled`.
- CSV upload status will be constrained to `pending`, `processing`, `completed`, and `failed`.
- Safety posture will be constrained to `conservative`, `balanced`, and `aggressive`.
- Notification type will be constrained to `critical_reorder`, `amber_reorder`, `daily_digest`, `weekly_insight`, `csv_upload_completed`, `csv_upload_failed`, and `purchase_order_draft`.
- Drug status will be constrained to `active`, `inactive`, and `unknown`.
- Chat message role will be constrained to `user`, `assistant`, and `system`.
- Drug DINs will be stored as text because leading zeros and external catalog formatting must be preserved. The application will normalize DIN input by trimming surrounding whitespace; it must not cast DIN values to integers.
- Drug-referencing tenant tables will store `din text` and enforce foreign keys to `drugs(din)`.
- Foreign keys will use restrictive delete behavior by default. Historical and audit-relevant records should not disappear through cascades.
- Location lifecycle will use `deactivated_at`; hard delete is not part of this phase.
- Quantities and count-like values will use integer types.
- Monetary values will use exact `numeric` values, with `cost_per_unit` represented as `numeric(12,4)`.
- Forecast metric values will use exact `numeric` values; `days_of_supply` will preserve one decimal place.
- `quantity_on_hand` is required on persisted dispensing records in this foundation. CSV rows missing current on-hand quantity should be rejected or staged by a later ingestion feature rather than inserted as incomplete dispensing records.
- `patient_id` will be nullable text and treated as an opaque, source-system pseudonymous identifier. It will not be constrained as globally unique because the same patient identifier may appear across multiple dispensing rows.
- `drug_thresholds` will be unique per `(location_id, din)` so each location has at most one active threshold configuration per drug.
- `notification_settings` will be unique per organization.
- Forecast rows will allow multiple generations over time for the same `(location_id, din, forecast_horizon_days)`. No uniqueness constraint will block reruns because historical generated forecasts are useful for audit and trend analysis.
- Purchase orders will require `grok_output` text in the schema because this table represents generated drafts. Manual purchase order creation without Grok output is out of scope for this foundation.
- Purchase order line items, notification payloads, and CSV validation summaries will be stored as nullable or required `jsonb` according to the domain table definition. JPA can map these as text-backed JSON strings or JDBC JSON types, as long as the database column remains `jsonb`.
- Chat messages will not include a conversation/session table in this foundation. Messages are scoped by location and ordered by `created_at`; conversation grouping can be added later if the assistant product needs multiple threads per location.
- Spring Boot will own persistence; Python services will not be called in this phase.
- The forecast service remains future numeric-only infrastructure and will not generate explanations or persist forecasts.
- The LLM service remains future language-only infrastructure and will not generate forecast numbers.
- The frontend remains out of scope and will not call Python services directly.
- Supabase RLS will be documented for tenant-owned tables, but Spring Boot must still validate organization and location ownership server-side in future service code.
- `app_users.id` will match the Supabase Auth user UUID represented by `auth.uid()` in RLS policies.
- RLS SQL will be delivered as a dedicated SQL artifact, separate from production Flyway migrations unless it is safe to run in both local PostgreSQL and Supabase. This keeps local development from depending on Supabase-only auth helpers while preserving deployable policy SQL for Supabase.
- RLS policies will allow public read access to `drugs` because drug catalog metadata is non-tenant catalog data.
- Tenant-owned table policies will derive organization access from `app_users.organization_id` for the current `auth.uid()`.
- Location-owned table policies will validate access through the owning location's organization.
- Backend service-role compatibility will be documented as relying on Supabase service role bypass behavior for server-side orchestration jobs, while requiring Spring Boot service code to enforce organization and location authorization before reads or writes.
- A local PostgreSQL Docker Compose setup will be provided for development and migration verification.
- Seed data will be dev-only and must not run in production.

## Module Sketch

- **Configuration module**: application profile configuration, security filter chain for the health endpoint, actuator exposure, and future infrastructure wiring.
- **Common module**: shared mapped superclass or embeddable conventions for UUID identifiers and database-managed timestamps, plus common domain helpers where useful.
- **Health surface**: a minimal public endpoint that returns operational status and timestamp without exposing business data.
- **Tenant modules**: organization, location, and application user persistence boundaries that future authorization services will depend on.
- **Catalog module**: drug catalog persistence centered on DIN as the natural business identifier.
- **Dispensing module**: dispensing history persistence with strict handling for quantities, on-hand inventory, optional cost, and opaque patient identifiers.
- **Forecast module**: forecast, threshold, safety posture, confidence, and reorder risk persistence without calling forecasting services.
- **Operational modules**: upload, notification, purchase order, and chat persistence boundaries for future workflows.
- **Billing, LLM, insights modules**: package placeholders only in this foundation unless needed for enum or repository ownership; no behavior is implemented.
- **Database module**: Flyway schema migrations, dev-only seed data, and Supabase RLS SQL artifact.

## Testing Decisions

- Tests should verify behavior and contracts through stable public interfaces, not private implementation details.
- The first public seam is the application health endpoint.
- The second public seam is application startup with production-style configuration validation.
- The third public seam is the database migration contract against PostgreSQL.
- Migration tests should run against PostgreSQL, preferably Testcontainers, so Flyway SQL and JPA mappings are checked against the real database dialect.
- JPA entity tests should focus on schema compatibility and repository availability, not field-by-field implementation trivia.
- RLS SQL should be syntactically separated and reviewable even if not automatically exercised in the first test baseline.
- Future feature work must use red-green-refactor cycles with one behavior-level test in flight at a time.
- The first TDD cycle should drive `GET /health` through HTTP and assert the response contract before implementation.
- The next TDD cycle should verify Flyway can apply all production migrations to PostgreSQL.
- A following TDD cycle should verify the Spring context starts with JPA validation against the migrated schema.
- Repository smoke tests may verify that each repository bean exists and can perform simple persistence operations where practical, but should not duplicate every database constraint as brittle unit tests.

## Out of Scope

- No authentication flow implementation.
- No organization or location authorization service implementation.
- No CSV upload parsing or ingestion.
- No forecast generation endpoint.
- No Prophet/FastAPI integration.
- No LLM/Grok integration.
- No chat assistant endpoint.
- No purchase order generation endpoint.
- No notification delivery jobs.
- No Resend integration.
- No Stripe billing integration.
- No frontend implementation.
- No production deployment configuration beyond profile-ready application configuration.
- No generated explanations, savings calculations, or medical/prescribing decision support.

## Further Notes

- The product supports inventory decisions only, not medical or prescribing decisions.
- Patient IDs may exist in the database but must not appear in logs, prompts, exports, chat context, generated purchase orders, or external LLM payloads.
- Only aggregated drug-level data may be sent to any future LLM service.
- The local seed data should be obviously non-production sample data.
- Supabase Canada region and Fly.io Toronto deployment are assumed future environment choices; this PRD focuses on the backend foundation required before deployment.
- The current repository contains a partial untracked scaffold created during planning. Implementation may keep, revise, or replace those files as long as the final result satisfies this PRD.
- Senior-default decisions above are intentionally decisive so implementation can proceed without further product grilling. Future feature PRDs may revisit them only when there is a concrete product reason.
