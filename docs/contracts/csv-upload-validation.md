# Implementation Handoff Contract

## 1. Summary

- Implemented the CSV Upload & Validation backend feature for PharmaForecast.
- Purpose: allow authenticated users to upload Kroll dispensing history CSV files per pharmacy location, validate the data, aggregate patient-level rows into location-specific daily drug demand, and update upload status for frontend polling/Realtime completion.
- In scope:
  - Multipart upload endpoint.
  - Location ownership guard.
  - Upload polling endpoints.
  - CSV parsing and validation using Apache Commons CSV.
  - All-or-nothing validation failure behavior.
  - Daily dispensing demand aggregation.
  - PostgreSQL upsert boundary for `dispensing_records`.
  - Dedicated async CSV processing executor.
  - Supabase Realtime completion broadcast client.
  - Async DIN enrichment service seam.
  - Forward Flyway migrations for upload statuses and dispensing upsert support.
  - PRD and implementation plan documentation.
- Out of scope:
  - Frontend upload UI.
  - Actual Health Canada DIN API implementation. The async seam exists, but enrichment body is `NOT IMPLEMENTED`.
  - Forecast generation after upload.
  - Email notification delivery.
  - Storing raw CSV files.
  - LLM integration.
- Owning repo/service/module:
  - Repository: `/Users/adamsaleh/Downloads/pharmacast-backend`
  - Service: Java Spring Boot backend
  - Primary package area: `ca.pharmaforecast.backend.upload`

## 2. Files Added or Changed

- `docs/prd/csv-upload-validation.md`
  - Created.
  - Product requirements document for CSV upload and validation.

- `plans/csv-upload-validation.md`
  - Created.
  - Tracer-bullet implementation plan derived from the PRD.

- `pom.xml`
  - Updated.
  - Added `org.apache.commons:commons-csv:1.12.0`.

- `.env.example`
  - Updated.
  - Added `PHARMAFORECAST_SUPABASE_URL` and `PHARMAFORECAST_SUPABASE_SERVICE_ROLE_KEY`.

- `src/main/resources/application.yml`
  - Updated.
  - Added multipart settings:
    - `spring.servlet.multipart.max-file-size=50MB`
    - `spring.servlet.multipart.max-request-size=50MB`

- `src/main/resources/db/migration/V5__standardize_csv_upload_statuses.sql`
  - Created.
  - Converts legacy CSV upload statuses to uppercase feature statuses and replaces the `ck_csv_uploads_status` constraint.

- `src/main/resources/db/migration/V6__support_csv_daily_demand_upsert.sql`
  - Created.
  - Drops `dispensing_records_din_fkey` if present.
  - Adds unique constraint `uq_dispensing_records_location_din_date` on `(location_id, din, dispensed_date)`.

- `src/main/java/ca/pharmaforecast/backend/config/AsyncConfig.java`
  - Created.
  - Enables async execution and defines `csvProcessingExecutor`.

- `src/main/java/ca/pharmaforecast/backend/upload/CsvUploadController.java`
  - Created.
  - Defines upload creation and polling HTTP endpoints.

- `src/main/java/ca/pharmaforecast/backend/upload/CsvUploadService.java`
  - Created.
  - Handles location ownership checks, upload shell creation, upload polling, and processing job submission after commit.

- `src/main/java/ca/pharmaforecast/backend/upload/CsvProcessingJob.java`
  - Created.
  - Interface for async CSV processing jobs.

- `src/main/java/ca/pharmaforecast/backend/upload/CsvProcessingService.java`
  - Created.
  - Implements `CsvProcessingJob`.
  - Parses CSV bytes, validates rows, aggregates valid rows, writes imports, persists upload result, triggers enrichment, and broadcasts completion.

- `src/main/java/ca/pharmaforecast/backend/upload/CsvUploadStatus.java`
  - Updated.
  - Replaced legacy enum values with `PENDING`, `PROCESSING`, `SUCCESS`, `ERROR`.

- `src/main/java/ca/pharmaforecast/backend/upload/CsvUpload.java`
  - Updated.
  - Added explicit getters and setters for upload fields used by services/tests.

- `src/main/java/ca/pharmaforecast/backend/upload/CsvUploadRepository.java`
  - Updated.
  - Added:
    - `findTop10ByLocationIdOrderByUploadedAtDesc(UUID locationId)`
    - `findByIdAndLocationId(UUID id, UUID locationId)`

- `src/main/java/ca/pharmaforecast/backend/dispensing/DailyDispensingRecord.java`
  - Created.
  - Immutable record for aggregated daily dispensing imports.

- `src/main/java/ca/pharmaforecast/backend/dispensing/DispensingRecordImportRepository.java`
  - Created.
  - JDBC batch upsert boundary for `dispensing_records`.

- `src/main/java/ca/pharmaforecast/backend/dispensing/DispensingRecordRepository.java`
  - Updated.
  - Added `findTopByLocationIdAndDinOrderByDispensedDateDesc(UUID locationId, String din)`.

- `src/main/java/ca/pharmaforecast/backend/drug/DinEnrichmentService.java`
  - Created.
  - Async enrichment seam. Body is currently `NOT IMPLEMENTED`.

- `src/main/java/ca/pharmaforecast/backend/realtime/SupabaseRealtimeClient.java`
  - Created.
  - Sends Supabase Realtime broadcast messages through REST when configured.

- `src/test/java/ca/pharmaforecast/backend/CsvUploadEndpointTest.java`
  - Created.
  - Covers upload shell creation, ownership rejection, polling list, polling detail, and job submission.

- `src/test/java/ca/pharmaforecast/backend/CsvProcessingServiceTest.java`
  - Created.
  - Covers missing-column failure, row validation failure, safe patient ID handling, aggregation, import handoff, Realtime broadcast calls, DIN enrichment trigger, and date-format warning/rejection behavior.

- `src/test/java/ca/pharmaforecast/backend/AuthTestRepositoryConfig.java`
  - Updated.
  - Adds in-memory fake `CsvUploadRepository`, mocked `DispensingRecordImportRepository`, and primary fake `CsvProcessingJob` for controller/auth tests.

## 3. Public Interface Contract

### `POST /locations/{locationId}/uploads`

- Name: Create CSV upload
- Type: HTTP endpoint
- Purpose: create a CSV upload shell for one location and submit async CSV processing.
- Owner: Spring Boot backend, `CsvUploadController`
- Inputs:
  - Path variable `locationId`: `UUID`, required.
  - Multipart field `file`: `MultipartFile`, required.
  - Bearer authentication: required.
- Outputs:
  - JSON object `CreateUploadResponse`
  - Fields:
    - `uploadId`: `UUID`
    - `status`: `CsvUploadStatus`
- Required fields:
  - `file`
  - `locationId`
- Optional fields:
  - None.
- Validation rules:
  - User must be authenticated.
  - `locationId` must exist.
  - Location must belong to `AuthenticatedUserPrincipal.organizationId()`.
  - Multipart size limit is configured at 50MB.
- Defaults:
  - If `file.getOriginalFilename()` is null or blank, stored filename defaults to `upload.csv`.
  - New upload status is `PENDING`.
- Status codes or result states:
  - `200 OK`: upload shell created and processing job submitted.
  - `401`: missing/invalid authentication, existing auth layer returns `{"error":"AUTHENTICATION_REQUIRED"}`.
  - `403`: authenticated user does not own the location.
  - `404`: location not found.
  - `400`: CSV file bytes could not be read.
- Error shapes:
  - Existing Spring/Security error handling applies for auth.
  - `ResponseStatusException` responses use Spring Boot default error shape unless a global handler is added later.
- Example input:
  - Method: `POST`
  - URL: `/locations/11111111-1111-1111-1111-111111111111/uploads`
  - Content-Type: `multipart/form-data`
  - Field: `file=@dispensing-history.csv`
- Example output:

```json
{
  "uploadId": "22222222-2222-2222-2222-222222222222",
  "status": "PENDING"
}
```

### `GET /locations/{locationId}/uploads`

- Name: List recent CSV uploads
- Type: HTTP endpoint
- Purpose: return the last 10 upload records for one location, newest first.
- Owner: Spring Boot backend, `CsvUploadController`
- Inputs:
  - Path variable `locationId`: `UUID`, required.
  - Bearer authentication: required.
- Outputs:
  - JSON array of `UploadResponse`.
- Required fields:
  - `locationId`
- Optional fields:
  - None.
- Validation rules:
  - User must be authenticated.
  - `locationId` must exist.
  - Location must belong to `AuthenticatedUserPrincipal.organizationId()`.
- Defaults:
  - Limit is fixed at 10 uploads.
  - Sort is fixed by `uploadedAt` descending.
- Status codes or result states:
  - `200 OK`: list returned.
  - `401`: missing/invalid authentication.
  - `403`: authenticated user does not own the location.
  - `404`: location not found.
- Error shapes:
  - Existing Spring/Security error handling applies for auth.
  - `ResponseStatusException` responses use Spring Boot default error shape unless a global handler is added later.
- Example input:
  - `GET /locations/11111111-1111-1111-1111-111111111111/uploads`
- Example output:

```json
[
  {
    "uploadId": "22222222-2222-2222-2222-222222222222",
    "filename": "dispensing-history.csv",
    "status": "SUCCESS",
    "rowCount": 1250,
    "drugCount": 317,
    "validationSummary": "{\"total_rows\":1250,\"valid_rows\":1250,\"invalid_rows\":0,\"unique_dins\":317,\"date_range_start\":\"2026-01-01\",\"date_range_end\":\"2026-04-19\",\"warnings\":[]}",
    "uploadedAt": "2026-04-19T16:00:00Z"
  }
]
```

### `GET /locations/{locationId}/uploads/{uploadId}`

- Name: Get single CSV upload
- Type: HTTP endpoint
- Purpose: return one upload record for one location.
- Owner: Spring Boot backend, `CsvUploadController`
- Inputs:
  - Path variable `locationId`: `UUID`, required.
  - Path variable `uploadId`: `UUID`, required.
  - Bearer authentication: required.
- Outputs:
  - JSON object `UploadResponse`.
- Required fields:
  - `locationId`
  - `uploadId`
- Optional fields:
  - None.
- Validation rules:
  - User must be authenticated.
  - `locationId` must exist.
  - Location must belong to `AuthenticatedUserPrincipal.organizationId()`.
  - `uploadId` must belong to `locationId`.
- Defaults:
  - None.
- Status codes or result states:
  - `200 OK`: upload returned.
  - `401`: missing/invalid authentication.
  - `403`: authenticated user does not own the location.
  - `404`: location not found or upload not found for that location.
- Error shapes:
  - Existing Spring/Security error handling applies for auth.
  - `ResponseStatusException` responses use Spring Boot default error shape unless a global handler is added later.
- Example input:
  - `GET /locations/11111111-1111-1111-1111-111111111111/uploads/22222222-2222-2222-2222-222222222222`
- Example output:

```json
{
  "uploadId": "22222222-2222-2222-2222-222222222222",
  "filename": "dispensing-history.csv",
  "status": "ERROR",
  "rowCount": null,
  "drugCount": null,
  "validationSummary": "{\"total_rows\":2,\"valid_rows\":1,\"invalid_rows\":1,\"unique_dins\":1,\"date_range_start\":\"2026-04-20\",\"date_range_end\":\"2026-04-20\",\"warnings\":[]}",
  "uploadedAt": "2026-04-19T16:00:00Z"
}
```

### `CsvProcessingJob.process(UUID uploadId, UUID locationId, byte[] csvBytes)`

- Name: CSV processing job
- Type: Java interface
- Purpose: async processing boundary used by `CsvUploadService`.
- Owner: `ca.pharmaforecast.backend.upload`
- Inputs:
  - `uploadId`: `UUID`, required.
  - `locationId`: `UUID`, required.
  - `csvBytes`: `byte[]`, required.
- Outputs:
  - No return value.
- Required fields:
  - All inputs are required.
- Optional fields:
  - None.
- Validation rules:
  - Implemented by `CsvProcessingService`.
- Defaults:
  - `CsvProcessingService.process` runs with `@Async("csvProcessingExecutor")`.
- Status codes or result states:
  - Mutates `CsvUpload.status` to `PROCESSING`, then `SUCCESS` or `ERROR`.
- Error shapes:
  - Validation errors are serialized as JSON array in `CsvUpload.errorMessage`.
- Example input:

```java
csvProcessingJob.process(uploadId, locationId, csvBytes);
```

- Example output:
  - No direct output. Read status through polling endpoints.

### Supabase Realtime Broadcast

- Name: Upload completion broadcast
- Type: External HTTP integration/event emitter
- Purpose: notify frontend subscribers that upload processing completed.
- Owner: `SupabaseRealtimeClient`
- Inputs:
  - `locationId`: `UUID`
  - `uploadId`: `UUID`
  - `status`: `CsvUploadStatus`
  - `validationSummary`: `String`
- Outputs:
  - HTTP POST to Supabase Realtime REST API.
- Required fields:
  - Config `pharmaforecast.supabase.url`
  - Config `pharmaforecast.supabase.service-role-key`
- Optional fields:
  - If Supabase config is missing, broadcast is skipped.
- Validation rules:
  - Payload must not include `patient_id`.
- Defaults:
  - Missing Supabase config causes no-op with debug log.
- Status codes or result states:
  - Broadcast success/failure does not change upload status.
- Error shapes:
  - Runtime exceptions are caught and logged as warnings.
- Example outbound request body:

```json
{
  "messages": [
    {
      "topic": "location:11111111-1111-1111-1111-111111111111",
      "event": "upload_complete",
      "payload": {
        "type": "upload_complete",
        "uploadId": "22222222-2222-2222-2222-222222222222",
        "status": "SUCCESS",
        "summary": "{\"total_rows\":3,\"valid_rows\":3,\"invalid_rows\":0,\"unique_dins\":2,\"date_range_start\":\"2026-04-19\",\"date_range_end\":\"2026-04-20\",\"warnings\":[]}"
      }
    }
  ]
}
```

## 4. Data Contract

### `CsvUploadStatus`

- Exact name: `ca.pharmaforecast.backend.upload.CsvUploadStatus`
- Type: Java enum
- Allowed values:
  - `PENDING`
  - `PROCESSING`
  - `SUCCESS`
  - `ERROR`
- Migration notes:
  - `V5__standardize_csv_upload_statuses.sql` maps:
    - `pending` -> `PENDING`
    - `processing` -> `PROCESSING`
    - `completed` -> `SUCCESS`
    - `failed` -> `ERROR`
  - Replaces SQL check constraint `ck_csv_uploads_status`.
- Backward compatibility notes:
  - This is a breaking status-value change for callers expecting lowercase or `completed`/`failed`.

### `CsvUpload`

- Exact name: `ca.pharmaforecast.backend.upload.CsvUpload`
- Type: JPA entity mapped to `csv_uploads`
- Fields:
  - `id`: `UUID`, required, database-managed.
  - `createdAt`: `Instant`, required, database-managed.
  - `updatedAt`: `Instant`, required, database-managed.
  - `locationId`: `UUID`, required.
  - `filename`: `String`, required.
  - `status`: `CsvUploadStatus`, required.
  - `errorMessage`: `String`, optional. Stores JSON array string of validation errors on failure.
  - `rowCount`: `Integer`, optional. Set on success.
  - `drugCount`: `Integer`, optional. Set on success.
  - `validationSummary`: `String`, optional. Stored in `jsonb` column but represented as String in Java.
  - `uploadedAt`: `Instant`, required.
- Allowed status values:
  - `PENDING`, `PROCESSING`, `SUCCESS`, `ERROR`
- Defaults:
  - New uploads are created with `PENDING`.
  - `filename` defaults to `upload.csv` only when multipart original filename is null or blank.

### `CreateUploadResponse`

- Exact name: `CsvUploadController.CreateUploadResponse`
- Type: Java record serialized as JSON
- Fields:
  - `uploadId`: `UUID`, required.
  - `status`: `CsvUploadStatus`, required.
- Example:

```json
{
  "uploadId": "22222222-2222-2222-2222-222222222222",
  "status": "PENDING"
}
```

### `UploadResponse`

- Exact name: `CsvUploadController.UploadResponse`
- Type: Java record serialized as JSON
- Fields:
  - `uploadId`: `UUID`, required.
  - `filename`: `String`, required.
  - `status`: `CsvUploadStatus`, required.
  - `rowCount`: `Integer`, optional.
  - `drugCount`: `Integer`, optional.
  - `validationSummary`: `String`, optional JSON string.
  - `uploadedAt`: `Instant`, required.
- Backward compatibility notes:
  - `validationSummary` is currently serialized as a string containing JSON, not as a nested JSON object.

### Validation Error JSON

- Exact shape: JSON array stored as `CsvUpload.errorMessage`.
- Fields per error:
  - `row`: `int`
  - `field`: `String`
  - `value`: `String`
  - `message`: `String`
- Required vs optional:
  - All fields are present.
- Sensitive data rule:
  - `patient_id` values are not written into validation errors by current validators.
- Example:

```json
[
  {
    "row": 2,
    "field": "din",
    "value": "12345",
    "message": "Row 2: DIN '12345' is invalid — DINs must be exactly 8 digits"
  }
]
```

### Validation Summary JSON

- Exact shape: JSON object stored as `CsvUpload.validationSummary`.
- Fields:
  - `total_rows`: integer
  - `valid_rows`: integer
  - `invalid_rows`: integer
  - `unique_dins`: integer
  - `date_range_start`: string date `YYYY-MM-DD` or null
  - `date_range_end`: string date `YYYY-MM-DD` or null
  - `warnings`: array of strings
- Example:

```json
{
  "total_rows": 3,
  "valid_rows": 3,
  "invalid_rows": 0,
  "unique_dins": 2,
  "date_range_start": "2026-04-19",
  "date_range_end": "2026-04-20",
  "warnings": []
}
```

### CSV Input Format

- Exact consumed file format: CSV with header row.
- Parser:
  - Apache Commons CSV `CSVFormat.DEFAULT`
  - `setHeader()`
  - `setSkipHeaderRecord(true)`
  - `setTrim(true)`
  - UTF-8 reader
- Mandatory columns, case-insensitive:
  - `dispensed_date`
  - `din`
  - `quantity_dispensed`
  - `quantity_on_hand`
- Optional columns, case-insensitive:
  - `cost_per_unit`
  - `patient_id`
- Validation:
  - `dispensed_date`: accepts `YYYY-MM-DD`, `dd/MM/yyyy`, and `MM/dd/yyyy`.
  - Ambiguous slash dates where both `dd/MM/yyyy` and `MM/dd/yyyy` parse to different dates are invalid.
  - Non-preferred slash dates are accepted only when unambiguous and add a summary warning.
  - `din`: must match exactly `\d{8}`.
  - `quantity_dispensed`: integer, must be `>= 0`.
  - `quantity_on_hand`: integer, must be `>= 0`.
  - `cost_per_unit`: optional; if present and non-blank, must be decimal `> 0`.
  - `patient_id`: optional; accepted but not included in errors, summaries, broadcasts, logs, or external payloads by this implementation.

### `DailyDispensingRecord`

- Exact name: `ca.pharmaforecast.backend.dispensing.DailyDispensingRecord`
- Type: Java record
- Fields:
  - `locationId`: `UUID`, required.
  - `din`: `String`, required.
  - `dispensedDate`: `LocalDate`, required.
  - `quantityDispensed`: `int`, required.
  - `quantityOnHand`: `int`, required.
  - `costPerUnit`: `BigDecimal`, optional.
- Aggregation rules:
  - Group key: `locationId`, `din`, `dispensedDate`.
  - `quantityDispensed`: sum.
  - `quantityOnHand`: minimum value in group.
  - `costPerUnit`: first non-null value retained by `mergeCost`; conflicting costs are not currently rejected.

### `dispensing_records`

- Exact table: `dispensing_records`
- Changed by migration:
  - `V6__support_csv_daily_demand_upsert.sql`
- Schema behavior changed:
  - Drops FK constraint `dispensing_records_din_fkey` if present so valid unknown DINs can be imported.
  - Adds `uq_dispensing_records_location_din_date` unique constraint on:
    - `location_id`
    - `din`
    - `dispensed_date`
- Upsert behavior:
  - `INSERT ... ON CONFLICT (location_id, din, dispensed_date) DO UPDATE`
  - Updates:
    - `quantity_dispensed`
    - `quantity_on_hand`
    - `cost_per_unit`
    - `updated_at = now()`
- Backward compatibility notes:
  - Removing the DIN FK is intentional to support unknown DIN ingestion.

## 5. Integration Contract

- Upstream dependencies:
  - Authenticated Spring Security context resolved to `AuthenticatedUserPrincipal`.
  - `LocationRepository.findById(UUID)` for ownership validation.
  - Multipart upload infrastructure from Spring Boot.
  - Apache Commons CSV.

- Downstream dependencies:
  - `CsvUploadRepository` for upload persistence and polling.
  - `DispensingRecordImportRepository` for aggregated dispensing record upsert.
  - `DinEnrichmentService.enrich(List<String> dins)` after successful import.
  - `SupabaseRealtimeClient.broadcastUploadComplete(UUID locationId, UUID uploadId, CsvUploadStatus status, String validationSummary)` after success or validation failure.

- Services called:
  - Supabase Realtime REST API when configured.
  - Actual Health Canada DIN API is `NOT IMPLEMENTED`.

- Endpoints hit:
  - `POST {pharmaforecast.supabase.url}/realtime/v1/api/broadcast`

- Events consumed:
  - No events consumed.

- Events published:
  - Supabase Realtime broadcast:
    - topic: `location:{locationId}`
    - event: `upload_complete`
    - payload fields: `type`, `uploadId`, `status`, `summary`

- Files read or written:
  - Uploaded CSV bytes are read from the multipart request into memory.
  - Raw CSV bytes are not stored on disk or in the database.
  - No generated files are written at runtime.

- Environment assumptions:
  - PostgreSQL supports `ON CONFLICT`.
  - Flyway migrations are applied before runtime.
  - Supabase Realtime config may be absent in local development.

- Auth assumptions:
  - `CurrentUserService.requireCurrentUser()` returns an `AuthenticatedUserPrincipal`.
  - `AuthenticatedUserPrincipal.organizationId()` is the tenant boundary used for location ownership.

- Retry behavior:
  - No explicit retry for CSV processing.
  - No explicit retry for Realtime broadcast.

- Timeout behavior:
  - No custom timeout configured for `RestClient`; default client behavior applies.

- Fallback behavior:
  - If Realtime config is missing, broadcast is skipped and upload status remains valid.
  - If Realtime call fails, exception is caught, warning is logged, and upload status remains valid.
  - Polling endpoints are the source of truth.

- Idempotency behavior:
  - Re-uploading the same clean CSV does not double-count daily demand because `dispensing_records` are upserted by `(location_id, din, dispensed_date)`.

## 6. Usage Instructions for Other Engineers

- Frontend engineers can rely on:
  - `POST /locations/{locationId}/uploads` returning immediately with `status: "PENDING"`.
  - `GET /locations/{locationId}/uploads` returning at most 10 uploads, newest first.
  - `GET /locations/{locationId}/uploads/{uploadId}` returning one upload.
  - Final statuses: `SUCCESS` or `ERROR`.
  - Active processing status: `PROCESSING`.
  - Polling as the fallback source of truth even when Realtime does not arrive.

- Backend engineers should call/import/use:
  - `CsvUploadService.createUpload(...)` for upload shell creation.
  - `CsvProcessingJob.process(...)` as the async processing abstraction.
  - `CsvProcessingService` for actual validation/import processing.
  - `DispensingRecordImportRepository.upsertAll(...)` for daily aggregated dispensing record imports.
  - `DispensingRecordRepository.findTopByLocationIdAndDinOrderByDispensedDateDesc(...)` for current stock lookup by forecast work.

- Required inputs:
  - `locationId` must be a UUID belonging to the authenticated user's organization.
  - Multipart field must be named `file`.
  - CSV must include mandatory columns.

- Outputs to handle:
  - Empty upload list: `[]`.
  - Loading state after POST: `PENDING`.
  - Active state: `PROCESSING`.
  - Success state: `SUCCESS` with `rowCount`, `drugCount`, and `validationSummary`.
  - Failure state: `ERROR` with `validationSummary`; detailed errors are persisted in `CsvUpload.errorMessage` but are not included in current `UploadResponse`.

- Finalized:
  - Route names.
  - Upload status enum values.
  - CSV mandatory/optional column names.
  - Location ownership rule.
  - Daily aggregation key.
  - Supabase Realtime topic/event names.

- Provisional:
  - `validationSummary` is returned as a JSON string, not an object.
  - `CsvUpload.errorMessage` is not currently exposed in `UploadResponse`.
  - `DinEnrichmentService.enrich(...)` is a stub.
  - `mergeCost(...)` keeps the first non-null cost for duplicate same-day DIN rows; conflicting costs are not rejected.

- MOCKED:
  - Tests use fake/mocked repositories and jobs in `AuthTestRepositoryConfig`.
  - Controller tests use a primary fake `CsvProcessingJob`.
  - Controller tests mock `DispensingRecordImportRepository`.
  - Service tests mock `SupabaseRealtimeClient`, `DinEnrichmentService`, and `DispensingRecordImportRepository`.

- Must not be changed without coordination:
  - `patient_id` must not be surfaced in errors, summaries, logs, Realtime payloads, exports, documents, or LLM prompts.
  - Upload status names because frontend and database constraints depend on them.
  - `(location_id, din, dispensed_date)` import key because it defines idempotency.

## 7. Security and Authorization Notes

- Auth requirements:
  - All upload endpoints require authenticated user context.
  - CORS preflight remains governed by existing security config.

- Permission rules:
  - User may create/list/read uploads only for locations whose `organization_id` equals `AuthenticatedUserPrincipal.organizationId()`.

- Tenancy rules:
  - CSV import is location-scoped.
  - Daily aggregation never crosses locations.
  - Polling endpoints validate location ownership before returning upload records.
  - Single upload lookup also requires upload to belong to the requested location.

- Role checks:
  - No role-specific authorization was added. Any authenticated app user with organization membership can use these upload endpoints if they can access the location.

- Data isolation:
  - Server-side location ownership check is enforced before upload creation and polling.
  - Supabase RLS is not relied on for the Spring Boot authorization decision.

- Sensitive fields:
  - `patient_id` may be present in CSV input.
  - Current implementation does not store `patient_id` in the aggregated upsert records because `DailyDispensingRecord` has no `patientId` field.
  - `patient_id` is not included in validation errors, validation summaries, Realtime payloads, or service logs by current code.

- Sanitization:
  - CSV fields are trimmed by Commons CSV.
  - DIN validation requires exactly eight digits.
  - Numeric fields are parsed into integer/decimal values.

- Forbidden fields:
  - `patient_id` must not be sent to Grok, any LLM, Realtime, logs, exports, generated purchase orders, or validation payloads.

- Logging restrictions:
  - `SupabaseRealtimeClient` logs only upload id on broadcast failure.
  - Do not add CSV row values or patient identifiers to logs.

- Compliance concerns:
  - The service reads raw CSV bytes in memory and discards them after processing.
  - No raw CSV storage was added.

## 8. Environment and Configuration

- `spring.servlet.multipart.max-file-size`
  - Purpose: maximum size for multipart file upload.
  - Required or optional: configured in `application.yml`.
  - Value: `50MB`.
  - Dev vs prod notes: applies to all profiles unless overridden.

- `spring.servlet.multipart.max-request-size`
  - Purpose: maximum size for multipart request.
  - Required or optional: configured in `application.yml`.
  - Value: `50MB`.
  - Dev vs prod notes: applies to all profiles unless overridden.

- `csvProcessingExecutor`
  - Purpose: executor bean for CSV async processing.
  - Required or optional: required for `CsvProcessingService.process`.
  - Runtime setting:
    - core pool size: `4`
    - max pool size: `4`
    - thread name prefix: `csv-processor-`

- `pharmaforecast.supabase.url`
  - Purpose: base Supabase project URL used by `SupabaseRealtimeClient`.
  - Required or optional: optional at runtime.
  - Default behavior if missing: Realtime broadcast is skipped.
  - Environment variable placeholder in `.env.example`: `PHARMAFORECAST_SUPABASE_URL`.

- `pharmaforecast.supabase.service-role-key`
  - Purpose: service role key used in Supabase Realtime `apikey` header.
  - Required or optional: optional at runtime.
  - Default behavior if missing: Realtime broadcast is skipped.
  - Environment variable placeholder in `.env.example`: `PHARMAFORECAST_SUPABASE_SERVICE_ROLE_KEY`.
  - Secret: yes. Do not expose to frontend.

## 9. Testing and Verification

- Tests added:
  - `CsvUploadEndpointTest`
    - Verifies owned-location multipart upload returns `PENDING`.
    - Verifies upload shell is persisted.
    - Verifies processing job receives upload id, location id, and CSV bytes.
    - Verifies upload creation rejects locations outside the user's organization.
    - Verifies recent upload polling.
    - Verifies single upload polling.
  - `CsvProcessingServiceTest`
    - Verifies missing mandatory columns mark upload `ERROR`.
    - Verifies safe validation details exclude patient IDs.
    - Verifies invalid row collection for date, DIN, quantity, and cost.
    - Verifies invalid files do not call import repository.
    - Verifies valid rows aggregate by location, DIN, and date.
    - Verifies success calls import repository, DIN enrichment, and Realtime client.
    - Verifies failure calls Realtime client but not DIN enrichment.
    - Verifies non-preferred date warnings and ambiguous date rejection.

- Tests updated:
  - `AuthTestRepositoryConfig`
    - Adds test fakes/mocks for upload repository, processing job, and import repository.

- Manual verification:
  - Ran `mvn test`.
  - Result: build success.
  - Test summary from last run: `Tests run: 20, Failures: 0, Errors: 0, Skipped: 4`.

- How to run tests:

```bash
mvn test
```

- How to locally validate the feature:
  - Start the Spring Boot app with database migrations applied.
  - Authenticate with a Supabase JWT that maps to an `app_users` row.
  - Submit multipart `POST /locations/{locationId}/uploads` with field `file`.
  - Poll `GET /locations/{locationId}/uploads/{uploadId}` until status is `SUCCESS` or `ERROR`.

- Known gaps in test coverage:
  - Testcontainers-backed Flyway/JPA tests skipped in the Codex sandbox because Docker socket access is blocked.
  - No live Supabase Realtime integration test.
  - No actual Health Canada DIN enrichment test because enrichment body is `NOT IMPLEMENTED`.
  - No test currently verifies `CsvUpload.errorMessage` through an HTTP response because `UploadResponse` does not expose it.

## 10. Known Limitations and TODOs

- `DinEnrichmentService.enrich(List<String> dins)` is `NOT IMPLEMENTED`; it is only an async seam.
- `UploadResponse` does not expose `CsvUpload.errorMessage`; frontend cannot currently retrieve full validation error array through the implemented detail endpoint.
- `validationSummary` is returned as a JSON string, not a nested JSON object.
- `mergeCost(...)` keeps the first non-null `cost_per_unit` when multiple same-location same-DIN same-date rows aggregate. It does not reject conflicting costs, despite the PRD noting this as a discrepancy candidate.
- DIN normalization currently requires exactly eight digits. It does not convert shorter numeric strings with leading zero padding.
- Realtime client has no custom timeout and no retry.
- Realtime broadcast payload uses `summary` as a string containing JSON, not an object.
- Async processing failures outside validation are not wrapped into a safe `ERROR` upload result by a broad catch block.
- `CsvProcessingService.process(...)` mutates upload status to `PROCESSING` but does not save that intermediate state before validation/import work.
- Raw CSV bytes are held in memory during async handoff. This satisfies "never store raw CSV bytes" but may be memory-heavy for concurrent 50MB uploads.
- Docker-backed migration validation was not executed in this sandbox.

## 11. Source of Truth Snapshot

- Final route names:
  - `POST /locations/{locationId}/uploads`
  - `GET /locations/{locationId}/uploads`
  - `GET /locations/{locationId}/uploads/{uploadId}`
- Final DTO/model names:
  - `CsvUploadController.CreateUploadResponse`
  - `CsvUploadController.UploadResponse`
  - `CsvUpload`
  - `DailyDispensingRecord`
  - `CsvProcessingJob`
  - `CsvProcessingService.ValidationError`
- Final enum/status values:
  - `PENDING`
  - `PROCESSING`
  - `SUCCESS`
  - `ERROR`
- Final event names:
  - Supabase topic: `location:{locationId}`
  - Supabase event: `upload_complete`
  - Payload `type`: `upload_complete`
- Final key file paths:
  - `src/main/java/ca/pharmaforecast/backend/upload/CsvUploadController.java`
  - `src/main/java/ca/pharmaforecast/backend/upload/CsvUploadService.java`
  - `src/main/java/ca/pharmaforecast/backend/upload/CsvProcessingService.java`
  - `src/main/java/ca/pharmaforecast/backend/dispensing/DispensingRecordImportRepository.java`
  - `src/main/java/ca/pharmaforecast/backend/realtime/SupabaseRealtimeClient.java`
  - `src/main/resources/db/migration/V5__standardize_csv_upload_statuses.sql`
  - `src/main/resources/db/migration/V6__support_csv_daily_demand_upsert.sql`
- Breaking changes from previous version:
  - CSV upload status values changed from lowercase `pending`, `processing`, `completed`, `failed` to uppercase `PENDING`, `PROCESSING`, `SUCCESS`, `ERROR`.
  - `dispensing_records` no longer requires a matching `drugs(din)` row after `V6__support_csv_daily_demand_upsert.sql`.

## 12. Copy-Paste Handoff for the Next Engineer

The CSV upload backend is implemented end to end for upload shell creation, ownership-guarded polling, async CSV processing, validation, daily aggregation, idempotent dispensing record upsert, Realtime completion broadcast, and DIN enrichment handoff. It is safe to depend on the three upload routes, the four upload statuses, the `CsvProcessingJob` seam, and the `(location_id, din, dispensed_date)` import key.

What remains to be built: frontend UI, actual Health Canada DIN enrichment, exposing full validation errors through an API response, stronger async failure handling, live Supabase Realtime verification, and Docker-backed migration verification outside the sandbox. Watch the traps: `patient_id` must never be surfaced, `validationSummary` is currently a JSON string, and `cost_per_unit` conflicts inside same-day aggregates are not rejected.

Read first: sections 3, 4, and 10. They define the public routes, data shapes, and known limitations that matter before extending this feature.
