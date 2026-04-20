# Plan: CSV Upload & Validation

> Source PRD: `docs/prd/csv-upload-validation.md`

## Architectural decisions

Durable decisions that apply across all phases:

- **Routes**: `POST /locations/{locationId}/uploads`, `GET /locations/{locationId}/uploads`, and `GET /locations/{locationId}/uploads/{uploadId}`.
- **Schema**: CSV upload statuses are `PENDING`, `PROCESSING`, `SUCCESS`, and `ERROR`; dispensing records are idempotently keyed by `location_id`, `din`, and `dispensed_date`; valid unknown DINs are importable.
- **Key models**: CSV uploads track filename, status, error details, row count, drug count, validation summary, and upload time. Dispensing records store location-specific daily DIN demand and stock snapshots.
- **Auth**: Every upload route requires an authenticated app user and server-side validation that the location belongs to the user's organization.
- **Validation**: Uploads are all-or-nothing. Validation collects all row errors, excludes patient identifiers from user-facing output, and persists a safe validation summary.
- **Aggregation**: Same-location, same-DIN, same-date rows are combined into one daily demand record before persistence.
- **External services**: Supabase Realtime is notified through server-side REST broadcast after completion; notification failure is non-fatal. DIN enrichment is triggered asynchronously after successful import.
- **Forecasting**: Upload success makes historical demand available but does not automatically generate forecasts.

---

## Phase 1: Upload Shell And Ownership Guard

**User stories**: 1, 2, 3, 4, 5, 32, 33, 34, 39

### What to build

Create the first end-to-end upload API slice. A signed-in user can submit a multipart CSV for a location in their organization, receive an immediate pending response, and read recent upload status through polling endpoints. Users cannot create or read uploads for locations outside their organization. The database and API expose the standardized upload statuses.

### Acceptance criteria

- [ ] `POST /locations/{locationId}/uploads` accepts multipart field `file` and returns `{ uploadId, status: "PENDING" }`.
- [ ] Upload creation persists filename, location, status, and upload timestamp without storing raw CSV bytes.
- [ ] Upload creation rejects locations outside the authenticated user's organization.
- [ ] `GET /locations/{locationId}/uploads` returns the last 10 uploads ordered newest first.
- [ ] `GET /locations/{locationId}/uploads/{uploadId}` returns one upload for the requested location.
- [ ] Upload status values are `PENDING`, `PROCESSING`, `SUCCESS`, and `ERROR` in Java, SQL constraints, and JSON responses.
- [ ] Multipart max file size is configured to 50MB.

---

## Phase 2: Validation Failure Path

**User stories**: 10, 11, 12, 14, 15, 23, 24, 25, 28, 29, 30, 31, 37

### What to build

Add the validation path that rejects unsafe files completely. The processor parses the CSV, validates mandatory columns and rows, collects all safe row errors, computes a summary where possible, marks the upload as error, and inserts no dispensing records.

### Acceptance criteria

- [ ] Mandatory headers are matched case-insensitively.
- [ ] Missing mandatory columns produce the Kroll re-export message.
- [ ] Invalid or ambiguous dates reject the upload.
- [ ] Invalid DINs reject the upload with row-specific safe messages.
- [ ] Zero quantities are accepted and negative quantities are rejected.
- [ ] Malformed or negative optional costs reject the upload when cost is present.
- [ ] Patient identifiers never appear in persisted error JSON or response payloads.
- [ ] Validation collects all row errors instead of failing fast.
- [ ] Failed validation marks the upload `ERROR` and imports no dispensing records.
- [ ] Validation summary includes row counts, unique DINs, date range when available, and warnings.

---

## Phase 3: Successful Import And Daily Aggregation

**User stories**: 9, 16, 19, 20, 21, 22, 26, 27, 35, 40

### What to build

Import clean files into location-specific daily demand. Valid rows are normalized, same-day same-DIN rows within the location are combined, and the aggregate records are upserted idempotently so retries do not double-count. Unknown DINs are allowed.

### Acceptance criteria

- [ ] Valid unknown DINs can be imported without a pre-existing drug catalog row.
- [ ] Same-location, same-DIN, same-date rows are combined before persistence.
- [ ] Combined `quantity_dispensed` is summed.
- [ ] Combined `quantity_on_hand` uses the lowest same-day value.
- [ ] Re-uploading the same clean file updates the same daily records without double-counting.
- [ ] Successful import marks the upload `SUCCESS`, stores row count, drug count, and summary.
- [ ] The latest-stock repository query returns the most recent record for a location and DIN.
- [ ] Upload success does not generate forecasts automatically.

---

## Phase 4: Async Processing And Completion Status

**User stories**: 5, 6, 8, 35, 38

### What to build

Move CSV processing behind a dedicated asynchronous processing boundary. Upload creation returns immediately, processing transitions status from pending to processing, and polling remains reliable until the final status is reached.

### Acceptance criteria

- [ ] CSV processing runs in a dedicated executor with four `csv-processor-` threads.
- [ ] Processing sets the upload to `PROCESSING` before validation/import work starts.
- [ ] Polling endpoints expose `PROCESSING` while work is active.
- [ ] Final polling result is `SUCCESS` or `ERROR` with the persisted summary.
- [ ] Async failures are converted into a safe `ERROR` upload result.

---

## Phase 5: Realtime Broadcast And Enrichment Trigger

**User stories**: 7, 8, 16, 17, 18, 36, 37

### What to build

Notify the frontend and enrichment pipeline when processing completes. Completion broadcasts are safe, location-topic scoped, and non-fatal. Successful imports trigger asynchronous DIN enrichment for imported DINs that need metadata.

### Acceptance criteria

- [ ] Completion broadcasts use topic `location:{locationId}` and event `upload_complete`.
- [ ] Broadcast payloads include upload id, final status, and safe summary information.
- [ ] Broadcast payloads do not include patient identifiers.
- [ ] Broadcast failure does not change upload success or error status.
- [ ] Successful imports trigger DIN enrichment asynchronously.
- [ ] Unknown DIN warnings are represented in the validation summary.

---

## Phase 6: Polish, Migration Coverage, And Edge-Case Hardening

**User stories**: all cross-cutting requirements

### What to build

Close the feature with migration validation, contract coverage, and edge-case hardening. Confirm the final API behavior, schema constraints, privacy guarantees, and parser warning behavior match the PRD.

### Acceptance criteria

- [ ] Flyway migrations apply cleanly and JPA validation passes against PostgreSQL.
- [ ] Response contracts are stable for success, failure, list, and detail endpoints.
- [ ] Date-format warnings are persisted for parseable non-preferred date formats.
- [ ] Unknown DIN warnings are persisted without blocking successful import.
- [ ] Error previews and full detail responses remain safe for patient privacy.
- [ ] The relevant automated test suite passes.
