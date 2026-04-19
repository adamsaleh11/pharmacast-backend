# Implementation Handoff Contract

## 1. Summary
- Implemented the PharmaForecast Spring Boot backend foundation in the `pharmaforecast-backend` service.
- Implemented Maven/Spring Boot 3.3.7 scaffolding with Java 21 target, Spring Security, Spring Data JPA, Flyway, PostgreSQL, validation, actuator, OAuth2 resource server dependency, Lombok, and Testcontainers test dependencies.
- Implemented one public health endpoint: `GET /health`.
- Implemented PostgreSQL production schema migration for organizations, locations, application users, drugs, dispensing records, forecasts, drug thresholds, stock adjustments, purchase orders, notifications, CSV uploads, chat messages, and notification settings.
- Implemented dev-only seed migration for one organization, one location, one owner user, five sample drugs, notification settings, and sample dispensing records.
- Implemented Supabase RLS SQL as a separate artifact, not as a production Flyway migration.
- Implemented JPA entities, enums, and repositories for all domain tables.
- In scope: backend scaffold, schema, RLS artifact, dev seed data, entities, repositories, health endpoint, local PostgreSQL compose file, baseline tests, PRD, plan, and this contract.
- Out of scope: auth flow implementation, organization/location authorization services, CSV parsing, forecasting service calls, LLM/Grok calls, notification delivery, Stripe billing, Resend email, frontend work, and business endpoints beyond `GET /health`.
- Owner: `ca.pharmaforecast.backend` Spring Boot backend.

## 2. Files Added or Changed
- `.gitignore`: created. Ignores `target/`, `.idea/`, `*.iml`, and `.DS_Store`.
- `pom.xml`: created. Defines Maven project `ca.pharmaforecast:pharmaforecast-backend:0.0.1-SNAPSHOT`, Spring Boot parent `3.3.7`, Java target `21`, Testcontainers `1.20.4`, required runtime dependencies, and test dependencies.
- `docker-compose.yml`: created. Defines local `postgres:16-alpine` service with database/user/password `pharmaforecast`, host port `5432`, named volume `postgres-data`, and `pg_isready` healthcheck.
- `docs/prd/foundation-schema-backend-setup.md`: created/updated. Source PRD with senior-default decisions.
- `plans/foundation-schema-backend-setup.md`: created/updated. Multi-phase implementation plan with TDD seam.
- `docs/contracts/foundation-schema-backend-setup.md`: created. This implementation handoff contract.
- `src/main/java/ca/pharmaforecast/backend/PharmaforecastBackendApplication.java`: created. Spring Boot application entry point.
- `src/main/java/ca/pharmaforecast/backend/config/ClockConfig.java`: created. Provides `Clock.systemUTC()` bean.
- `src/main/java/ca/pharmaforecast/backend/config/SecurityConfig.java`: created. Stateless security chain, permits `/health`, `/actuator/health`, `/actuator/health/**`, denies all other requests.
- `src/main/java/ca/pharmaforecast/backend/common/HealthController.java`: created. Implements `GET /health` and `HealthResponse(String status, Instant timestamp)`.
- `src/main/java/ca/pharmaforecast/backend/common/BaseEntity.java`: created. Mapped superclass for database-managed `id`, `createdAt`, and `updatedAt`.
- `src/main/java/ca/pharmaforecast/backend/auth/User.java`: created. JPA entity mapped to `app_users`.
- `src/main/java/ca/pharmaforecast/backend/auth/UserRole.java`: created. Enum values `owner`, `admin`, `pharmacist`, `staff`.
- `src/main/java/ca/pharmaforecast/backend/auth/UserRepository.java`: created. `JpaRepository<User, UUID>`.
- `src/main/java/ca/pharmaforecast/backend/organization/Organization.java`: created. JPA entity mapped to `organizations`.
- `src/main/java/ca/pharmaforecast/backend/organization/OrganizationRepository.java`: created. `JpaRepository<Organization, UUID>`.
- `src/main/java/ca/pharmaforecast/backend/location/Location.java`: created. JPA entity mapped to `locations`.
- `src/main/java/ca/pharmaforecast/backend/location/LocationRepository.java`: created. `JpaRepository<Location, UUID>`.
- `src/main/java/ca/pharmaforecast/backend/drug/Drug.java`: created. JPA entity mapped to `drugs`.
- `src/main/java/ca/pharmaforecast/backend/drug/DrugStatus.java`: created. Enum values `active`, `inactive`, `unknown`.
- `src/main/java/ca/pharmaforecast/backend/drug/DrugRepository.java`: created. `JpaRepository<Drug, UUID>`.
- `src/main/java/ca/pharmaforecast/backend/dispensing/DispensingRecord.java`: created. JPA entity mapped to `dispensing_records`.
- `src/main/java/ca/pharmaforecast/backend/dispensing/DispensingRecordRepository.java`: created. `JpaRepository<DispensingRecord, UUID>`.
- `src/main/java/ca/pharmaforecast/backend/forecast/Forecast.java`: created. JPA entity mapped to `forecasts`.
- `src/main/java/ca/pharmaforecast/backend/forecast/ForecastConfidence.java`: created. Enum values `low`, `medium`, `high`.
- `src/main/java/ca/pharmaforecast/backend/forecast/ReorderStatus.java`: created. Enum values `ok`, `amber`, `red`.
- `src/main/java/ca/pharmaforecast/backend/forecast/SafetyMultiplier.java`: created. Enum values `conservative`, `balanced`, `aggressive`.
- `src/main/java/ca/pharmaforecast/backend/forecast/ForecastRepository.java`: created. `JpaRepository<Forecast, UUID>`.
- `src/main/java/ca/pharmaforecast/backend/forecast/DrugThreshold.java`: created. JPA entity mapped to `drug_thresholds`.
- `src/main/java/ca/pharmaforecast/backend/forecast/DrugThresholdRepository.java`: created. `JpaRepository<DrugThreshold, UUID>`.
- `src/main/java/ca/pharmaforecast/backend/forecast/StockAdjustment.java`: created. JPA entity mapped to `stock_adjustments`.
- `src/main/java/ca/pharmaforecast/backend/forecast/StockAdjustmentRepository.java`: created. `JpaRepository<StockAdjustment, UUID>`.
- `src/main/java/ca/pharmaforecast/backend/purchaseorder/PurchaseOrder.java`: created. JPA entity mapped to `purchase_orders`.
- `src/main/java/ca/pharmaforecast/backend/purchaseorder/PurchaseOrderStatus.java`: created. Enum values `draft`, `reviewed`, `sent`, `cancelled`.
- `src/main/java/ca/pharmaforecast/backend/purchaseorder/PurchaseOrderRepository.java`: created. `JpaRepository<PurchaseOrder, UUID>`.
- `src/main/java/ca/pharmaforecast/backend/notification/Notification.java`: created. JPA entity mapped to `notifications`.
- `src/main/java/ca/pharmaforecast/backend/notification/NotificationType.java`: created. Enum values `critical_reorder`, `amber_reorder`, `daily_digest`, `weekly_insight`, `csv_upload_completed`, `csv_upload_failed`, `purchase_order_draft`.
- `src/main/java/ca/pharmaforecast/backend/notification/NotificationRepository.java`: created. `JpaRepository<Notification, UUID>`.
- `src/main/java/ca/pharmaforecast/backend/notification/NotificationSettings.java`: created. JPA entity mapped to `notification_settings`.
- `src/main/java/ca/pharmaforecast/backend/notification/NotificationSettingsRepository.java`: created. `JpaRepository<NotificationSettings, UUID>`.
- `src/main/java/ca/pharmaforecast/backend/upload/CsvUpload.java`: created. JPA entity mapped to `csv_uploads`.
- `src/main/java/ca/pharmaforecast/backend/upload/CsvUploadStatus.java`: created. Enum values `pending`, `processing`, `completed`, `failed`.
- `src/main/java/ca/pharmaforecast/backend/upload/CsvUploadRepository.java`: created. `JpaRepository<CsvUpload, UUID>`.
- `src/main/java/ca/pharmaforecast/backend/chat/ChatMessage.java`: created. JPA entity mapped to `chat_messages`.
- `src/main/java/ca/pharmaforecast/backend/chat/ChatRole.java`: created. Enum values `user`, `assistant`, `system`.
- `src/main/java/ca/pharmaforecast/backend/chat/ChatMessageRepository.java`: created. `JpaRepository<ChatMessage, UUID>`.
- `src/main/java/ca/pharmaforecast/backend/billing/package-info.java`: created. Keeps requested package present; no billing behavior implemented.
- `src/main/java/ca/pharmaforecast/backend/insights/package-info.java`: created. Keeps requested package present; no insights behavior implemented.
- `src/main/java/ca/pharmaforecast/backend/llm/package-info.java`: created. Keeps requested package present; no LLM behavior implemented.
- `src/main/resources/application.yml`: created. Defines default `local` profile, datasource, JPA validation, Flyway locations, actuator health exposure, `server.port`, and `prod` profile env var bindings.
- `src/main/resources/db/migration/V1__foundation_schema.sql`: created. Production schema migration with tables, constraints, indexes, and update timestamp triggers.
- `src/main/resources/db/dev-migration/V100__dev_seed_data.sql`: created. Local/dev-only seed data migration.
- `src/main/resources/db/supabase/rls_policies.sql`: created. Supabase RLS artifact with helper function and policies.
- `src/test/java/ca/pharmaforecast/backend/HealthEndpointTest.java`: created. HTTP-level behavior test for `GET /health`.
- `src/test/java/ca/pharmaforecast/backend/FlywayMigrationTest.java`: created. Testcontainers PostgreSQL migration contract test; skipped automatically when Docker is unavailable.
- `src/test/java/ca/pharmaforecast/backend/PersistenceContextTest.java`: created. Testcontainers Spring/JPA schema validation and repository smoke test; skipped automatically when Docker is unavailable.

## 3. Public Interface Contract

### `GET /health`
- Name: `GET /health`
- Type: HTTP endpoint
- Purpose: Custom application health endpoint for deployment/runtime checks.
- Owner: `ca.pharmaforecast.backend.common.HealthController`
- Inputs: none
- Outputs: JSON object with `status` and `timestamp`
- Required fields: none
- Optional fields: none
- Validation rules: none
- Defaults: `status` is always `"ok"`; `timestamp` is generated from the injected UTC `Clock`
- Status codes: `200 OK` on success
- Error shapes: NOT IMPLEMENTED; no custom error shape was added for this endpoint
- Example input: `GET /health`
- Example output:
```json
{
  "status": "ok",
  "timestamp": "2026-04-19T05:48:19.307Z"
}
```

### `GET /actuator/health`
- Name: `GET /actuator/health`
- Type: Spring Boot Actuator endpoint
- Purpose: Actuator health endpoint exposed by Spring Boot configuration.
- Owner: Spring Boot Actuator, configured by `src/main/resources/application.yml` and permitted by `SecurityConfig`.
- Inputs: none
- Outputs: Spring Boot actuator health response
- Required fields: none
- Optional fields: none
- Validation rules: none
- Defaults: actuator health probes enabled via `management.endpoint.health.probes.enabled=true`
- Status codes: Spring Boot actuator-managed status codes
- Error shapes: Spring Boot actuator-managed error shapes
- Example input: `GET /actuator/health`
- Example output: actuator-managed, not customized in this implementation

### HTTP Security Contract
- Name: `SecurityFilterChain securityFilterChain(HttpSecurity http)`
- Type: Spring Security configuration
- Purpose: Allow health checks and deny all other HTTP requests until real auth/business APIs are implemented.
- Owner: `ca.pharmaforecast.backend.config.SecurityConfig`
- Inputs: HTTP requests
- Outputs: authorization result
- Required fields: none
- Optional fields: none
- Validation rules: request path must match an allowed path to be permitted
- Defaults: stateless sessions; CSRF disabled; HTTP Basic disabled; form login disabled; logout disabled; CORS defaults enabled
- Status codes or result states: `/health`, `/actuator/health`, `/actuator/health/**` are permitted; all other routes are denied
- Error shapes: Spring Security default denial response
- Example input: `GET /health`
- Example output: request reaches `HealthController`

### Maven Commands
- Name: `mvn test`
- Type: CLI command
- Purpose: Compile and run the test suite.
- Owner: Maven project in `pom.xml`
- Inputs: project source tree
- Outputs: Maven test result
- Required fields: Maven installed; dependency download access; Java capable of compiling release `21`
- Optional fields: Docker runtime for Testcontainers-backed PostgreSQL tests
- Validation rules: tests must compile; health endpoint test must pass; Testcontainers tests run only when Docker is available
- Defaults: Spring default profile is `local`
- Status codes or result states: build success or build failure
- Error shapes: Maven/Surefire output
- Example input: `mvn test`
- Example output: `BUILD SUCCESS`; in the current environment, 3 tests discovered, 1 passed, 2 skipped because Docker is unavailable

### Config Contract
- Name: `application.yml`
- Type: Spring Boot YAML configuration
- Purpose: Defines local/prod runtime behavior.
- Owner: backend service
- Inputs: Spring profiles and environment variables
- Outputs: configured Spring Boot application context
- Required fields in prod: `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`
- Optional fields: none added
- Validation rules: JPA uses `ddl-auto: validate`; Flyway enabled
- Defaults: profile `local`; server port `8080`; local datasource `jdbc:postgresql://localhost:5432/pharmaforecast`; local username/password `pharmaforecast`/`pharmaforecast`
- Example input: `SPRING_PROFILES_ACTIVE=prod DATABASE_URL=... DATABASE_USERNAME=... DATABASE_PASSWORD=...`
- Example output: production profile uses `classpath:db/migration` only

## 4. Data Contract

### Shared Table Columns
- Exact names: `id`, `created_at`, `updated_at`
- Field types: `id uuid PRIMARY KEY DEFAULT gen_random_uuid()`, `created_at timestamptz NOT NULL DEFAULT now()`, `updated_at timestamptz NOT NULL DEFAULT now()`
- Required vs optional: all required
- Defaults: database-generated UUID and timestamps
- Validation constraints: primary key on `id`
- Migration notes: `pgcrypto` extension is enabled by `V1__foundation_schema.sql`; `set_updated_at()` trigger function updates `updated_at` before updates
- Backward compatibility notes: initial schema only

### `organizations`
- Fields: `id uuid`, `created_at timestamptz`, `updated_at timestamptz`, `name text NOT NULL`, `stripe_customer_id text`, `subscription_status text`, `trial_ends_at timestamptz`
- Required: `id`, `created_at`, `updated_at`, `name`
- Optional: `stripe_customer_id`, `subscription_status`, `trial_ends_at`
- Allowed values: no check constraint for `subscription_status`
- Defaults: shared defaults only
- Validation constraints: primary key
- JPA entity: `ca.pharmaforecast.backend.organization.Organization`
- Repository: `OrganizationRepository`

### `locations`
- Fields: `id uuid`, `created_at timestamptz`, `updated_at timestamptz`, `organization_id uuid NOT NULL`, `name text NOT NULL`, `address text NOT NULL`, `deactivated_at timestamptz`
- Required: `id`, `created_at`, `updated_at`, `organization_id`, `name`, `address`
- Optional: `deactivated_at`
- Defaults: shared defaults only
- Validation constraints: `organization_id REFERENCES organizations(id) ON DELETE RESTRICT`
- Indexes: `idx_locations_organization_id`
- JPA entity: `ca.pharmaforecast.backend.location.Location`
- Repository: `LocationRepository`

### `app_users`
- Fields: `id uuid`, `created_at timestamptz`, `updated_at timestamptz`, `organization_id uuid NOT NULL`, `email text NOT NULL`, `role text NOT NULL`
- Required: all fields
- Optional: none
- Allowed values: `role IN ('owner', 'admin', 'pharmacist', 'staff')`
- Defaults: shared defaults only
- Validation constraints: `organization_id REFERENCES organizations(id) ON DELETE RESTRICT`, `uq_app_users_email UNIQUE (email)`, `ck_app_users_role`
- Indexes: `idx_app_users_organization_id`
- JPA entity: `ca.pharmaforecast.backend.auth.User`
- Repository: `UserRepository`
- Migration note: physical table name is `app_users`, not `users`, to avoid conflict with Supabase `auth.users`

### `drugs`
- Fields: `id uuid`, `created_at timestamptz`, `updated_at timestamptz`, `din text NOT NULL`, `name text NOT NULL`, `strength text NOT NULL`, `form text NOT NULL`, `therapeutic_class text NOT NULL`, `manufacturer text NOT NULL`, `status text NOT NULL`, `last_refreshed_at timestamptz NOT NULL`
- Required: all fields
- Optional: none
- Allowed values: `status IN ('active', 'inactive', 'unknown')`
- Defaults: shared defaults only
- Validation constraints: `uq_drugs_din UNIQUE (din)`, `ck_drugs_din_not_blank`, `ck_drugs_status`
- Indexes: `idx_drugs_din`
- JPA entity: `ca.pharmaforecast.backend.drug.Drug`
- Repository: `DrugRepository`
- Backward compatibility note: DIN is text and must preserve leading zeros

### `dispensing_records`
- Fields: `id uuid`, `created_at timestamptz`, `updated_at timestamptz`, `location_id uuid NOT NULL`, `din text NOT NULL`, `dispensed_date date NOT NULL`, `quantity_dispensed integer NOT NULL`, `quantity_on_hand integer NOT NULL`, `cost_per_unit numeric(12,4)`, `patient_id text`
- Required: `id`, `created_at`, `updated_at`, `location_id`, `din`, `dispensed_date`, `quantity_dispensed`, `quantity_on_hand`
- Optional: `cost_per_unit`, `patient_id`
- Allowed values: `quantity_dispensed >= 0`, `quantity_on_hand >= 0`, `cost_per_unit IS NULL OR cost_per_unit >= 0`
- Defaults: shared defaults only
- Validation constraints: `location_id REFERENCES locations(id) ON DELETE RESTRICT`, `din REFERENCES drugs(din) ON DELETE RESTRICT`
- Indexes: `idx_dispensing_records_location_din_date`
- JPA entity: `ca.pharmaforecast.backend.dispensing.DispensingRecord`
- Repository: `DispensingRecordRepository`
- Security note: `patient_id` is nullable opaque text and must not be logged, exported, sent to LLMs, used in prompts, added to chat context, or included in purchase orders

### `forecasts`
- Fields: `id uuid`, `created_at timestamptz`, `updated_at timestamptz`, `location_id uuid NOT NULL`, `din text NOT NULL`, `generated_at timestamptz NOT NULL`, `forecast_horizon_days integer NOT NULL`, `predicted_quantity integer NOT NULL`, `confidence text NOT NULL`, `days_of_supply numeric(12,1) NOT NULL`, `reorder_status text NOT NULL`, `prophet_lower numeric(12,2) NOT NULL`, `prophet_upper numeric(12,2) NOT NULL`, `avg_daily_demand numeric(12,2)`, `reorder_point numeric(12,2)`, `data_points_used integer`
- Required: all except `avg_daily_demand`, `reorder_point`, `data_points_used`
- Optional: `avg_daily_demand`, `reorder_point`, `data_points_used`
- Allowed values: `forecast_horizon_days IN (7, 14, 30)`, `predicted_quantity >= 0`, `confidence IN ('low', 'medium', 'high')`, `reorder_status IN ('ok', 'amber', 'red')`, `days_of_supply >= 0`, `prophet_lower <= prophet_upper`, `data_points_used IS NULL OR data_points_used >= 14`
- Defaults: shared defaults only
- Validation constraints: `location_id REFERENCES locations(id) ON DELETE RESTRICT`, `din REFERENCES drugs(din) ON DELETE RESTRICT`
- Indexes: `idx_forecasts_location_din_generated_at`
- JPA entity: `ca.pharmaforecast.backend.forecast.Forecast`
- Repository: `ForecastRepository`
- Migration note: forecast reruns are allowed; no uniqueness constraint blocks repeated generations

### `drug_thresholds`
- Fields: `id uuid`, `created_at timestamptz`, `updated_at timestamptz`, `location_id uuid NOT NULL`, `din text NOT NULL`, `lead_time_days integer NOT NULL DEFAULT 2`, `red_threshold_days integer NOT NULL DEFAULT 3`, `amber_threshold_days integer NOT NULL DEFAULT 7`, `safety_multiplier text NOT NULL DEFAULT 'balanced'`, `notifications_enabled boolean NOT NULL DEFAULT true`
- Required: all fields
- Optional: none
- Allowed values: `lead_time_days >= 0`, `red_threshold_days >= 0`, `amber_threshold_days >= red_threshold_days`, `safety_multiplier IN ('conservative', 'balanced', 'aggressive')`
- Defaults: `lead_time_days=2`, `red_threshold_days=3`, `amber_threshold_days=7`, `safety_multiplier='balanced'`, `notifications_enabled=true`
- Validation constraints: `location_id REFERENCES locations(id) ON DELETE RESTRICT`, `din REFERENCES drugs(din) ON DELETE RESTRICT`, `uq_drug_thresholds_location_din UNIQUE (location_id, din)`
- JPA entity: `ca.pharmaforecast.backend.forecast.DrugThreshold`
- Repository: `DrugThresholdRepository`

### `stock_adjustments`
- Fields: `id uuid`, `created_at timestamptz`, `updated_at timestamptz`, `location_id uuid NOT NULL`, `din text NOT NULL`, `adjustment_quantity integer NOT NULL`, `adjusted_at timestamptz NOT NULL`, `note text NOT NULL`
- Required: all fields
- Optional: none
- Defaults: shared defaults only
- Validation constraints: `location_id REFERENCES locations(id) ON DELETE RESTRICT`, `din REFERENCES drugs(din) ON DELETE RESTRICT`
- Indexes: `idx_stock_adjustments_location_din_adjusted_at`
- JPA entity: `ca.pharmaforecast.backend.forecast.StockAdjustment`
- Repository: `StockAdjustmentRepository`

### `purchase_orders`
- Fields: `id uuid`, `created_at timestamptz`, `updated_at timestamptz`, `location_id uuid NOT NULL`, `generated_at timestamptz NOT NULL`, `grok_output text NOT NULL`, `line_items jsonb`, `status text NOT NULL`
- Required: `id`, `created_at`, `updated_at`, `location_id`, `generated_at`, `grok_output`, `status`
- Optional: `line_items`
- Allowed values: `status IN ('draft', 'reviewed', 'sent', 'cancelled')`
- Defaults: shared defaults only
- Validation constraints: `location_id REFERENCES locations(id) ON DELETE RESTRICT`, `ck_purchase_orders_status`
- JPA entity: `ca.pharmaforecast.backend.purchaseorder.PurchaseOrder`
- Repository: `PurchaseOrderRepository`
- JSON mapping: `lineItems` uses Hibernate `@JdbcTypeCode(SqlTypes.JSON)` and database column `jsonb`

### `notifications`
- Fields: `id uuid`, `created_at timestamptz`, `updated_at timestamptz`, `organization_id uuid NOT NULL`, `location_id uuid`, `type text NOT NULL`, `payload jsonb NOT NULL DEFAULT '{}'::jsonb`, `sent_at timestamptz`, `read_at timestamptz`
- Required: `id`, `created_at`, `updated_at`, `organization_id`, `type`, `payload`
- Optional: `location_id`, `sent_at`, `read_at`
- Allowed values: `type IN ('critical_reorder', 'amber_reorder', 'daily_digest', 'weekly_insight', 'csv_upload_completed', 'csv_upload_failed', 'purchase_order_draft')`
- Defaults: `payload='{}'::jsonb`; shared defaults
- Validation constraints: `organization_id REFERENCES organizations(id) ON DELETE RESTRICT`, `location_id REFERENCES locations(id) ON DELETE RESTRICT`, `ck_notifications_type`
- Indexes: `idx_notifications_organization_sent_at`
- JPA entity: `ca.pharmaforecast.backend.notification.Notification`
- Repository: `NotificationRepository`
- JSON mapping: `payload` uses Hibernate `@JdbcTypeCode(SqlTypes.JSON)` and database column `jsonb`

### `csv_uploads`
- Fields: `id uuid`, `created_at timestamptz`, `updated_at timestamptz`, `location_id uuid NOT NULL`, `filename text NOT NULL`, `status text NOT NULL`, `error_message text`, `row_count integer`, `drug_count integer`, `validation_summary jsonb`, `uploaded_at timestamptz NOT NULL`
- Required: `id`, `created_at`, `updated_at`, `location_id`, `filename`, `status`, `uploaded_at`
- Optional: `error_message`, `row_count`, `drug_count`, `validation_summary`
- Allowed values: `status IN ('pending', 'processing', 'completed', 'failed')`, `row_count IS NULL OR row_count >= 0`, `drug_count IS NULL OR drug_count >= 0`
- Defaults: shared defaults only
- Validation constraints: `location_id REFERENCES locations(id) ON DELETE RESTRICT`, `ck_csv_uploads_status`
- Indexes: `idx_csv_uploads_location_uploaded_at`
- JPA entity: `ca.pharmaforecast.backend.upload.CsvUpload`
- Repository: `CsvUploadRepository`
- JSON mapping: `validationSummary` uses Hibernate `@JdbcTypeCode(SqlTypes.JSON)` and database column `jsonb`

### `chat_messages`
- Fields: `id uuid`, `created_at timestamptz`, `updated_at timestamptz`, `location_id uuid NOT NULL`, `role text NOT NULL`, `content text NOT NULL`
- Required: all fields
- Optional: none
- Allowed values: `role IN ('user', 'assistant', 'system')`
- Defaults: shared defaults only
- Validation constraints: `location_id REFERENCES locations(id) ON DELETE RESTRICT`, `ck_chat_messages_role`
- Indexes: `idx_chat_messages_location_created_at`
- JPA entity: `ca.pharmaforecast.backend.chat.ChatMessage`
- Repository: `ChatMessageRepository`
- Migration note: no conversation/session table was added

### `notification_settings`
- Fields: `id uuid`, `created_at timestamptz`, `updated_at timestamptz`, `organization_id uuid NOT NULL`, `daily_digest_enabled boolean NOT NULL DEFAULT true`, `weekly_insights_enabled boolean NOT NULL DEFAULT true`, `critical_alerts_enabled boolean NOT NULL DEFAULT true`
- Required: all fields
- Optional: none
- Defaults: `daily_digest_enabled=true`, `weekly_insights_enabled=true`, `critical_alerts_enabled=true`; shared defaults
- Validation constraints: `organization_id REFERENCES organizations(id) ON DELETE RESTRICT`, `uq_notification_settings_organization UNIQUE (organization_id)`
- JPA entity: `ca.pharmaforecast.backend.notification.NotificationSettings`
- Repository: `NotificationSettingsRepository`

### Supabase RLS Artifact
- Exact file: `src/main/resources/db/supabase/rls_policies.sql`
- Function: `public.current_app_user_organization_id() RETURNS uuid`
- Public read policy: `drugs_public_read` on `drugs` for `anon, authenticated`
- Service role policies: `<table>_service_role_all` for all schema tables
- Organization-scoped authenticated policies: `organizations_member_access`, `locations_member_access`, `app_users_member_access`, `notification_settings_member_access`, `notifications_member_access`
- Location-scoped authenticated policies: `dispensing_records_member_access`, `forecasts_member_access`, `drug_thresholds_member_access`, `stock_adjustments_member_access`, `purchase_orders_member_access`, `csv_uploads_member_access`, `chat_messages_member_access`
- Migration notes: not included in `spring.flyway.locations`; apply separately in Supabase

### Dev Seed Data
- Exact file: `src/main/resources/db/dev-migration/V100__dev_seed_data.sql`
- Runs in profile: `local`
- Does not run in profile: `prod`
- Seed organization ID: `00000000-0000-0000-0000-000000000001`
- Seed location ID: `00000000-0000-0000-0000-000000000101`
- Seed owner user ID: `00000000-0000-0000-0000-000000000201`
- Seed notification settings ID: `00000000-0000-0000-0000-000000000301`
- Seed DINs: `02242903`, `02471477`, `02247618`, `02345678`, `02012345`
- Seed dispensing records: 8 rows, all with `patient_id NULL`

## 5. Integration Contract
- Upstream dependencies: Java runtime capable of Maven `release 21`; Maven; PostgreSQL for runtime; Docker only for Testcontainers tests and local compose workflow.
- Downstream dependencies: PostgreSQL database; Supabase for production PostgreSQL/Auth/RLS; no downstream Python, LLM, email, billing, or frontend calls are implemented.
- Services called: NOT IMPLEMENTED. No external service calls are made.
- Endpoints hit: NOT IMPLEMENTED. The backend does not call external HTTP endpoints.
- Events consumed: NOT IMPLEMENTED.
- Events published: NOT IMPLEMENTED.
- Files read or written at runtime: Flyway reads `classpath:db/migration`; local profile also reads `classpath:db/dev-migration`; Supabase RLS SQL is a deployable artifact but is not loaded by the application.
- Environment assumptions: local PostgreSQL is available at `localhost:5432` when running the app with the default `local` profile; production config uses `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD`.
- Auth assumptions: no auth flow is implemented. Future Supabase-authenticated user identity is expected to map `auth.uid()` to `app_users.id`.
- Retry behavior: NOT IMPLEMENTED.
- Timeout behavior: NOT IMPLEMENTED.
- Fallback behavior: Testcontainers tests are skipped when Docker is unavailable because they use `@Testcontainers(disabledWithoutDocker = true)`.
- Idempotency behavior: production migration is Flyway-versioned as `V1__foundation_schema.sql`; dev seed is versioned as `V100__dev_seed_data.sql`.

## 6. Usage Instructions for Other Engineers
- Use `OrganizationRepository`, `LocationRepository`, `UserRepository`, `DrugRepository`, `DispensingRecordRepository`, `ForecastRepository`, `DrugThresholdRepository`, `StockAdjustmentRepository`, `PurchaseOrderRepository`, `NotificationRepository`, `NotificationSettingsRepository`, `CsvUploadRepository`, and `ChatMessageRepository` as the persistence entry points for future services.
- Use `GET /health` for a custom lightweight health check. Do not add business data to this response.
- Use `src/main/resources/db/migration/V1__foundation_schema.sql` as the production schema source of truth.
- Use `src/main/resources/db/supabase/rls_policies.sql` as the Supabase RLS source of truth. Apply it separately to Supabase after production migrations exist.
- Use `docker-compose.yml` to start local PostgreSQL if not using Supabase locally.
- Provide `location_id` and `din` when writing location/drug-scoped data.
- Preserve DIN values as text. Do not parse DINs as integers.
- Treat `patient_id` as sensitive opaque text. Do not add it to logs, LLM payloads, chat context, exports, or purchase orders.
- Handle empty states in future APIs by returning empty collections from service/API layers; repository behavior is standard Spring Data JPA behavior, but no API layer is implemented yet.
- Handle success/failure states in future services using the database enum/status values documented in this contract.
- Finalized: table names, enum values, UUID/timestamp ownership, default threshold values, Flyway locations, health endpoint response fields.
- Provisional: no conversation/session grouping for chat messages; no explicit enum for `organizations.subscription_status`.
- MOCKED: no mocks are implemented.
- Must not be changed without coordination: `app_users.id = Supabase auth.uid()` assumption, `app_users` table name, patient data restrictions, `din text`, and Spring Boot ownership of persistence/authorization.

## 7. Security and Authorization Notes
- No authentication flow is implemented.
- No organization/location authorization service is implemented.
- Spring Security currently permits only `/health`, `/actuator/health`, and `/actuator/health/**`; all other HTTP requests are denied.
- Supabase RLS SQL is provided but not applied by Flyway.
- RLS assumes `app_users.id` equals Supabase `auth.uid()`.
- RLS helper function `public.current_app_user_organization_id()` reads the current authenticated user's organization.
- RLS permits public read access for `drugs`.
- RLS scopes tenant-owned organization rows by `organization_id`.
- RLS scopes location-owned rows through the owning `locations.organization_id`.
- RLS includes `service_role` all-access policies for backend orchestration compatibility.
- Spring Boot must still validate organization/location ownership server-side in future API/service code.
- Sensitive field: `dispensing_records.patient_id`.
- Forbidden uses of `patient_id`: logs, prompts, exports, chat context, external LLM payloads, and generated purchase orders.
- LLM sanitization is NOT IMPLEMENTED.
- CSV upload validation is NOT IMPLEMENTED.
- Billing authorization is NOT IMPLEMENTED.
- Role checks are NOT IMPLEMENTED beyond the `app_users.role` schema constraint.

## 8. Environment and Configuration
- `spring.application.name`: purpose is Spring application naming; required; default `pharmaforecast-backend`.
- `spring.profiles.default`: purpose is default profile selection; required; default `local`.
- `spring.datasource.url`: purpose is JDBC connection URL; required for app startup with JPA/Flyway; local default `jdbc:postgresql://localhost:5432/pharmaforecast`; prod value `${DATABASE_URL}`.
- `spring.datasource.username`: purpose is database username; required; local default `pharmaforecast`; prod value `${DATABASE_USERNAME}`.
- `spring.datasource.password`: purpose is database password; required; local default `pharmaforecast`; prod value `${DATABASE_PASSWORD}`.
- `spring.jpa.hibernate.ddl-auto`: purpose is schema validation; required; value `validate`.
- `spring.jpa.open-in-view`: purpose is disable Open Session in View; required; value `false`.
- `spring.jpa.properties.hibernate.jdbc.time_zone`: purpose is UTC JDBC timestamp handling; required; value `UTC`.
- `spring.jpa.properties.hibernate.format_sql`: purpose is formatted SQL logging; local default `true`; prod value `false`.
- `spring.flyway.enabled`: purpose is migration enablement; required; value `true`.
- `spring.flyway.locations`: purpose is migration path selection; base value `classpath:db/migration`; local value `classpath:db/migration,classpath:db/dev-migration`; prod value `classpath:db/migration`.
- `management.endpoints.web.exposure.include`: purpose is actuator exposure; required; value `health`.
- `management.endpoint.health.probes.enabled`: purpose is health probes; required; value `true`.
- `server.port`: purpose is HTTP port; optional; default in config `8080`.
- `DATABASE_URL`: production JDBC URL; required in `prod`; no default in `prod`.
- `DATABASE_USERNAME`: production database username; required in `prod`; no default in `prod`.
- `DATABASE_PASSWORD`: production database password; required in `prod`; no default in `prod`.
- Docker Compose environment: `POSTGRES_DB=pharmaforecast`, `POSTGRES_USER=pharmaforecast`, `POSTGRES_PASSWORD=pharmaforecast`.

## 9. Testing and Verification
- Added `HealthEndpointTest`: verifies `GET /health` returns HTTP `200 OK`, body field `status` equals `"ok"`, and body field `timestamp` is a non-blank string.
- Added `FlywayMigrationTest`: verifies production Flyway migrations apply to PostgreSQL via Testcontainers. It is skipped when Docker is unavailable.
- Added `PersistenceContextTest`: verifies Spring context starts with PostgreSQL/Testcontainers, Flyway migrations, JPA `ddl-auto=validate`, and all repository beans present. It is skipped when Docker is unavailable.
- Manual verification command run: `mvn test`.
- Final observed result: `BUILD SUCCESS`.
- Final observed test summary: 3 tests discovered, 1 executed and passed, 2 skipped.
- Reason for skipped tests: Docker daemon was not running; Testcontainers reported no valid Docker environment and skipped because tests use `@Testcontainers(disabledWithoutDocker = true)`.
- How to run all tests with database verification: start Docker Desktop, then run `mvn test`.
- How to locally validate the app database: run `docker compose up -d postgres`, then run the Spring Boot application with the default `local` profile.
- Known coverage gap: PostgreSQL migration and JPA validation tests were not executed in the current environment because Docker was unavailable.
- Known coverage gap: Supabase RLS SQL artifact was not applied to a Supabase database during this implementation.

## 10. Known Limitations and TODOs
- Docker Desktop was not running in the current environment, so Testcontainers-backed database tests were skipped during final verification.
- Supabase RLS SQL is not automatically applied by Flyway.
- No auth flow is implemented.
- No JWT validation behavior is implemented beyond dependencies and future-ready security package structure.
- No organization/location authorization service is implemented.
- No CSV parsing or upload endpoint is implemented.
- No forecasting endpoint or Python forecast service integration is implemented.
- No LLM/Grok service integration is implemented.
- No notification job or email delivery is implemented.
- No Stripe billing integration is implemented.
- No frontend integration is implemented.
- `organizations.subscription_status` is `text` with no check constraint.
- JSONB fields are mapped as `String` with Hibernate JSON JDBC type; no structured Java DTOs exist for JSON payloads yet.
- Chat persistence has no conversation/session table.
- Purchase orders require `grok_output`; manual purchase order creation is not modeled.
- RLS policies rely on Supabase roles/functions and must be reviewed in Supabase before production rollout.
- Maven was installed with Homebrew during verification because it was missing from the environment.

## 11. Source of Truth Snapshot
- Final route names: `GET /health`, `GET /actuator/health`.
- Final response DTO/model name: `HealthController.HealthResponse`.
- Final table names: `organizations`, `locations`, `app_users`, `drugs`, `dispensing_records`, `forecasts`, `drug_thresholds`, `stock_adjustments`, `purchase_orders`, `notifications`, `csv_uploads`, `chat_messages`, `notification_settings`.
- Final repository names: `OrganizationRepository`, `LocationRepository`, `UserRepository`, `DrugRepository`, `DispensingRecordRepository`, `ForecastRepository`, `DrugThresholdRepository`, `StockAdjustmentRepository`, `PurchaseOrderRepository`, `NotificationRepository`, `NotificationSettingsRepository`, `CsvUploadRepository`, `ChatMessageRepository`.
- Final enum values: `UserRole owner/admin/pharmacist/staff`; `DrugStatus active/inactive/unknown`; `ForecastConfidence low/medium/high`; `ReorderStatus ok/amber/red`; `SafetyMultiplier conservative/balanced/aggressive`; `PurchaseOrderStatus draft/reviewed/sent/cancelled`; `NotificationType critical_reorder/amber_reorder/daily_digest/weekly_insight/csv_upload_completed/csv_upload_failed/purchase_order_draft`; `CsvUploadStatus pending/processing/completed/failed`; `ChatRole user/assistant/system`.
- Final migration path: `src/main/resources/db/migration/V1__foundation_schema.sql`.
- Final dev seed path: `src/main/resources/db/dev-migration/V100__dev_seed_data.sql`.
- Final RLS path: `src/main/resources/db/supabase/rls_policies.sql`.
- Breaking changes from previous version: physical application user table is `app_users`, not `users`.

## 12. Copy-Paste Handoff for the Next Engineer
The backend foundation is implemented: Maven Spring Boot scaffold, `GET /health`, security deny-by-default posture, full PostgreSQL schema migration, dev seed migration, Supabase RLS artifact, JPA entities/enums/repositories, local PostgreSQL compose, and baseline tests.

It is safe to depend on the table names, enum values, repository names, UUID/timestamp database ownership, `din text` identity, `app_users.id = auth.uid()` assumption, and `GET /health` response contract.

Remaining work: implement auth validation, organization/location authorization services, CSV ingestion, forecast generation orchestration, LLM sanitization/calls, alerts, purchase order drafting behavior, billing, frontend APIs, and production deployment wiring.

Traps: do not rename `app_users` to `users` without Supabase coordination; do not cast DINs to integers; do not expose `patient_id`; do not put RLS SQL into local Flyway unless Supabase-specific functions are handled; start Docker Desktop before expecting Testcontainers tests to execute.

Read first: sections 3, 4, and 7 for the public endpoint, database contract, and security boundaries.
