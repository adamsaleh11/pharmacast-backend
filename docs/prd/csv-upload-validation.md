# CSV Upload & Validation PRD

## Problem Statement

Independent pharmacy owners need a trustworthy way to load Kroll dispensing history into PharmaForecast before forecasts, reorder alerts, savings calculations, and purchase order workflows can be useful. Today the backend has the tenant schema, auth boundary, and upload persistence scaffold, but it does not let a pharmacist upload dispensing history, validate whether the export is safe to use, or convert patient-level dispense rows into the location-specific drug demand totals used by forecasting.

This matters because pharmacy inventory decisions depend on clean historical demand. A malformed file, ambiguous date, invalid DIN, negative quantity, or tenant mix-up could distort recommendations for critical medications. At the same time, pharmacy staff need the upload flow to be operationally forgiving where the data is still safe: valid unknown DINs should not block demand forecasting, multiple same-drug same-day patient fills should become daily drug totals, and a retried upload should not double-count demand.

The feature must preserve PharmaForecast's compliance boundary. Patient identifiers may appear in source CSVs, but they must never appear in user-facing upload summaries, validation errors, notifications, logs, exports, generated documents, LLM prompts, or external API payloads.

## Solution

Build a backend CSV ingestion pipeline for location-scoped dispensing history. A signed-in user uploads a CSV for one pharmacy location. The backend verifies that the location belongs to the authenticated user's organization, records the upload as pending, and returns immediately. A dedicated asynchronous processor validates the file, computes a validation summary, either rejects the whole upload with actionable errors or imports the whole clean file into location-specific daily drug demand records.

The upload flow is all-or-nothing. If validation fails, no dispensing records are imported and the upload moves to an error state. If validation succeeds, valid patient-level rows are transformed into daily totals per location, DIN, and dispensed date, then upserted idempotently so retrying the same clean file does not double-count demand.

Realtime broadcast informs the frontend when processing completes, while polling endpoints remain the source of truth. A successful upload makes historical demand available for later on-demand forecast generation; it does not automatically run Prophet.

## User Stories

1. As a pharmacy owner, I want to upload a Kroll dispensing history CSV for one location, so that PharmaForecast can learn that pharmacy's historical drug demand.
2. As a pharmacy owner, I want each location's upload to stay scoped to that location, so that forecasts remain independent per pharmacy.
3. As a signed-in user, I want the backend to reject uploads for locations outside my organization, so that tenant data cannot cross organizational boundaries.
4. As a pharmacist, I want the upload request to return immediately with a pending status, so that I am not blocked by CSV validation and import time.
5. As a pharmacist, I want to see an upload transition from pending to processing, so that I know the backend has started validating the file.
6. As a pharmacist, I want to leave the upload page while processing continues, so that I can keep working while the import runs asynchronously.
7. As a pharmacist, I want completion to update live when possible, so that I do not need to manually refresh the page.
8. As a pharmacist, I want polling to show the correct upload status even if live updates fail, so that the upload list remains reliable.
9. As a pharmacy owner, I want a clean CSV to import completely, so that forecasts use the full dispensing history I provided.
10. As a pharmacy owner, I want malformed CSVs rejected completely, so that unsafe or unclear data never partially changes my forecasts.
11. As a pharmacist, I want missing mandatory columns called out clearly, so that I know when to re-export from Kroll using the correct report.
12. As a pharmacist, I want invalid dates called out clearly, so that I can correct Kroll export settings before retrying.
13. As a pharmacist, I want parseable non-preferred date formats accepted with a warning, so that safe exports do not fail unnecessarily.
14. As a pharmacy owner, I want ambiguous dates rejected, so that demand is not imported under the wrong day.
15. As a pharmacist, I want invalid DINs rejected with row-specific messages, so that I can correct source data before import.
16. As a pharmacy owner, I want valid but unenriched DINs accepted, so that forecasting can still run from real demand history.
17. As a pharmacist, I want unknown DINs displayed as their DIN value with an unknown-drug label, so that the app does not invent drug metadata.
18. As a pharmacy owner, I want Health Canada enrichment warnings separated from validation errors, so that I know the upload succeeded even when some metadata is unavailable.
19. As a pharmacy owner, I want multiple fills of the same drug on the same day at one location to be combined, so that forecasts use total daily demand rather than individual patient requests.
20. As a pharmacy owner, I want same-drug same-day rows combined only within the selected location, so that demand is never aggregated across pharmacies.
21. As a pharmacy owner, I want daily demand to sum all same-location same-DIN same-date dispensed quantities, so that the forecast reflects total quantity needed.
22. As a pharmacy owner, I want daily stock on hand to use the lowest same-day value, so that reorder calculations stay conservative and independent of CSV row order.
23. As a pharmacist, I want zero dispensed quantity accepted when it appears in a clean export, so that harmless operational rows do not block import.
24. As a pharmacist, I want negative dispensed quantities rejected, so that invalid demand cannot reduce forecast history.
25. As a pharmacy owner, I want malformed or negative cost values rejected when cost is present, so that savings calculations are not built on bad money data.
26. As a pharmacy owner, I want missing cost values accepted, so that demand forecasting still works even when the export lacks cost data.
27. As a pharmacy owner, I want uploading the same clean file twice to be idempotent, so that retrying after uncertainty does not double-count demand.
28. As a pharmacist, I want upload errors to show row number, field, value when safe, and clear instructions, so that I can repair the file quickly.
29. As a pharmacist, I want the upload screen to show a manageable error preview, so that a badly malformed file does not make the interface unusable.
30. As a pharmacist, I want full error details available from the upload detail endpoint, so that support or power users can inspect everything that failed.
31. As a compliance reviewer, I want patient identifiers excluded from upload errors, summaries, notifications, logs, exports, documents, and prompts, so that patient privacy is preserved.
32. As a backend operator, I want raw CSV bytes discarded after parsing, so that the system does not retain unnecessary sensitive source files.
33. As a frontend engineer, I want a status endpoint for the last uploads, so that the upload history can show recent activity for a location.
34. As a frontend engineer, I want a single-upload endpoint with validation summary, so that the UI can render the details page for success or failure.
35. As a pharmacy owner, I want a successful upload to make historical demand available without automatically generating forecasts, so that I control when forecast generation runs.
36. As a pharmacist, I want a success notification to say data is ready for forecasting, so that my next action is clear.
37. As a pharmacist, I want a failure notification to say no records were imported, so that I know forecast data was not partially changed.
38. As a backend engineer, I want the ingestion processor to use a dedicated thread pool, so that large CSV work does not consume unrelated async capacity.
39. As a backend engineer, I want upload statuses to be consistent across the API and database, so that frontend and backend logic do not translate between mismatched terms.
40. As a forecast feature developer, I want a repository query for the latest record by location and DIN, so that forecast generation can read current stock from imported history.

## Implementation Decisions

- The upload endpoint is `POST /locations/{locationId}/uploads`.
- The upload endpoint consumes `multipart/form-data` with the file field named `file`.
- Multipart max file size is configured as 50MB.
- The upload endpoint validates that the requested location belongs to the authenticated user's organization before creating an upload record.
- The upload endpoint creates a CSV upload record with status `PENDING` and returns `{ uploadId, status }` immediately.
- CSV upload statuses are standardized as `PENDING`, `PROCESSING`, `SUCCESS`, and `ERROR` across Java, database, and API responses.
- A forward Flyway migration will update existing upload status constraints and values rather than editing already-applied migrations.
- CSV processing runs asynchronously in a dedicated executor with four threads named with the `csv-processor-` prefix.
- The processor updates the upload to `PROCESSING` at the start of async work.
- The backend never stores raw CSV bytes.
- Apache Commons CSV is used for parsing rather than ad hoc string parsing.
- Mandatory headers are matched case-insensitively: `dispensed_date`, `din`, `quantity_dispensed`, and `quantity_on_hand`.
- Optional headers are matched case-insensitively: `cost_per_unit` and `patient_id`.
- Missing mandatory columns fail validation with pharmacy-friendly messages that instruct the user to re-export from Kroll using the Dispensing History report.
- Dates are parsed as `YYYY-MM-DD`, `dd/MM/yyyy`, or `MM/dd/yyyy`.
- `YYYY-MM-DD` is the preferred date format.
- Parseable non-preferred date formats are accepted with warnings when interpretation is safe.
- Ambiguous or invalid dates reject the upload.
- DINs are stored as exactly eight-character strings.
- DIN validation rejects values that are not exactly eight digits after safe normalization.
- Valid unknown DINs are accepted into dispensing demand history.
- The schema will no longer require each dispensing record DIN to already exist in the drug catalog.
- Health Canada enrichment handles catalog metadata asynchronously after successful import.
- Unknown DINs are surfaced to users as `DIN ########` with an unknown-drug label until enrichment succeeds.
- `quantity_dispensed` accepts zero and positive integers.
- Negative `quantity_dispensed` rejects the upload.
- `quantity_on_hand` accepts zero and positive integers.
- Negative `quantity_on_hand` rejects the upload.
- `cost_per_unit` is optional.
- When present, `cost_per_unit` must be a positive decimal value.
- `patient_id` may be read from the CSV and stored as source data, but it is never included in user-facing responses, notifications, logs, exports, generated documents, LLM prompts, or external API payloads.
- Validation collects all row errors instead of failing fast.
- Validation summary is always computed when enough structure exists to inspect rows.
- Validation summary includes total rows, valid rows, invalid rows, unique DINs, date range start, date range end, and warnings.
- The API stores validation errors as a JSON array in the upload error field when validation fails.
- The upload detail response may expose full validation errors, but patient identifiers remain excluded.
- The main upload UI should display only a manageable preview, such as the first 50 row errors, with the total error count.
- Partial success is not allowed.
- On validation failure, the upload is marked `ERROR`, no dispensing records are imported, validation summary is persisted, and a Realtime completion event is emitted.
- On validation success, rows are normalized into location-specific daily totals before persistence.
- Same-location, same-DIN, same-date rows are combined.
- Combined daily `quantity_dispensed` is the sum of all valid rows for that location, DIN, and date.
- Combined daily `quantity_on_hand` is the lowest value seen for that location, DIN, and date.
- Combined daily `cost_per_unit` should use a deterministic validated value for the aggregate; if conflicting non-empty costs appear for the same location, DIN, and date, the implementation should reject the file as discrepant unless a later product decision defines cost aggregation.
- Dispensing records are upserted using the unique key `location_id`, `din`, and `dispensed_date`.
- A forward migration adds the unique constraint required for PostgreSQL `ON CONFLICT`.
- The import write path uses native PostgreSQL upsert behavior rather than JPA-only inserts.
- Re-uploading the same clean file is idempotent and does not double-count demand.
- On validation success, the upload is marked `SUCCESS`, row count, drug count, and validation summary are persisted.
- On validation success, DIN enrichment is triggered asynchronously with the list of DINs that need catalog enrichment.
- Realtime broadcast sends to topic `location:{locationId}` with event `upload_complete`.
- Realtime payloads include upload id, status, and safe summary information.
- Realtime payloads never include patient identifiers.
- Realtime broadcast uses Supabase's REST broadcast API with server-side service role credentials.
- Realtime broadcast failure is non-fatal; upload status remains based on validation and import outcome.
- Status polling endpoint `GET /locations/{locationId}/uploads` returns the last 10 uploads ordered by upload time descending.
- Status polling endpoint `GET /locations/{locationId}/uploads/{uploadId}` returns a single upload with validation summary and safe error details.
- Both polling endpoints validate that the location belongs to the authenticated user's organization.
- Single-upload polling also validates that the requested upload belongs to the requested location.
- A dispensing record repository query returns the latest record by location and DIN ordered by dispensed date descending.

## Testing Decisions

- Tests should exercise public behavior and stable contracts rather than private parser implementation details.
- Controller tests should verify authenticated upload creation, location ownership enforcement, multipart contract, immediate pending response, and polling responses.
- Authorization tests should verify users cannot create or read uploads for locations outside their organization.
- CSV parser/service tests should cover mandatory header detection, case-insensitive headers, accepted date formats, ambiguous date rejection, DIN normalization, unknown DIN acceptance, quantity validation, optional cost validation, and patient ID exclusion from errors.
- Validation tests should verify all row errors are collected instead of failing fast.
- Validation tests should verify the summary is computed with total rows, valid rows, invalid rows, unique DINs, date range, and warnings.
- Import tests should verify all-or-nothing behavior: invalid files mark the upload as error and insert no dispensing records.
- Import tests should verify successful uploads aggregate same-location same-DIN same-date rows into daily totals.
- Import tests should verify re-uploading the same clean file updates existing daily records without double-counting.
- Persistence tests should verify the Flyway migration adds the upload status changes, removes the blocking unknown-DIN foreign key behavior, and adds the unique import key.
- Repository tests should verify the latest-stock lookup returns the most recent dispensing record for a location and DIN.
- Realtime client tests should verify the outbound payload shape and headers without calling the real Supabase API.
- Async orchestration tests should verify upload status transitions from pending to processing to success or error at the service boundary.
- Existing Testcontainers-backed Flyway and persistence tests are the closest prior art for schema and JPA validation, with the known caveat that Docker-backed tests may be skipped in the Codex sandbox.

## Out of Scope

- Frontend upload UI implementation.
- Forecast generation after upload.
- Automatic Prophet runs after upload.
- LLM explanations, chat, or purchase order generation.
- Sending patient identifiers to any external API.
- Long-term raw CSV storage.
- Manual CSV correction inside PharmaForecast.
- Cross-location aggregation of dispensing history.
- Admin override workflows for importing invalid files.
- Stripe billing changes.
- Resend email notifications.
- Full Health Canada DIN enrichment implementation beyond triggering the enrichment service interface after successful import.
- Purchase order or savings calculation behavior beyond preserving cost data for future use.

## Further Notes

- This feature depends on the existing foundation schema and authentication/onboarding work.
- The backend package currently uses the `ca.pharmaforecast.backend` root, and implementation should follow the current repo convention unless a separate package-root migration is explicitly planned.
- The current schema and upload enum use older lowercase status values; this PRD intentionally supersedes them for this feature through forward migrations.
- The current dispensing schema links DINs to the drug catalog; this PRD requires valid unknown DINs to be importable, so the implementation must remove or avoid that blocking relationship.
- The product rule is: upload a clean Kroll export for one pharmacy location; if it is safe and interpretable, import it completely into location-specific daily drug demand totals; if anything could compromise forecast accuracy or patient privacy, reject the whole file and explain what to fix.
