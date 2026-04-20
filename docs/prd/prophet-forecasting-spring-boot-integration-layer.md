## Problem Statement

Independent pharmacies need a reliable way to turn dispensing history into actionable drug demand forecasts without exposing patient data or crossing tenant boundaries. The Python Prophet forecast service already exists as the numeric forecasting engine, but the Spring Boot backend does not yet have the integration layer needed to call it, persist the results, and expose forecast APIs to the frontend.

This matters now because pharmacists need on-demand forecasting for replenishment decisions, and the system must stay safe in a regulated Ontario/Canada healthcare context. Forecast generation must remain location-scoped, organization-scoped, and privacy-preserving, while still being fast enough for real pharmacy workflows.

## Solution

Build a Spring Boot forecasting integration layer that sits between the authenticated frontend and the Python `forecast_service`.

The backend will validate that the requested location belongs to the current user’s organization, assemble forecast requests from local database data, call the Python service, persist the returned forecasts, stream batch results back to the frontend, and provide a read API for the latest forecasts at a location.

The solution also includes a short-lived in-memory cache to prevent duplicate forecast generation when a pharmacist clicks Generate twice, plus supplemental cross-location demand history aggregation for low-data scenarios.

## User Stories

1. As a pharmacist, I want to generate a forecast for one DIN at one location, so that I can quickly assess reorder risk for a specific drug.
2. As a pharmacist, I want to generate forecasts for all drugs at a location, so that I can review the full inventory picture in one pass.
3. As a pharmacist, I want batch forecast results to stream as they are ready, so that I can see progress without waiting for the whole run to finish.
4. As a pharmacist, I want a forecast list filtered by status, search term, and horizon, so that I can focus on the items that matter most.
5. As a pharmacist, I want the forecast list to show drug name, strength, current stock, and threshold context, so that I can make informed operational decisions.
6. As a pharmacist, I want forecast results saved in the database, so that I can review prior forecasts and compare them over time.
7. As a pharmacist, I want repeated clicks on Generate to avoid duplicate work, so that I do not waste time or compute.
8. As a pharmacist, I want forecasts to respect my organization’s locations, so that I cannot see or generate forecasts for another tenant.
9. As a pharmacy owner, I want the backend to enforce location ownership, so that tenant isolation does not depend on the frontend.
10. As a pharmacy owner, I want forecasting to work even when the latest drug-specific location history is sparse, so that useful predictions still appear for low-volume drugs.
11. As a pharmacy owner, I want the backend to use aggregated history from other locations in my organization when allowed, so that network demand can strengthen low-data forecasts.
12. As a pharmacist, I want threshold defaults to apply when a drug has no custom threshold, so that new or unconfigured drugs still forecast correctly.
13. As a pharmacist, I want the forecast service call to fail gracefully when the Python service is down, so that the UI can show a clear unavailable state instead of breaking.
14. As a pharmacist, I want notification checks to run from the same forecast integration layer, so that alerts use the same source of truth as manual generation.
15. As a pharmacist, I want the system to calculate current stock from dispensing history and stock adjustments, so that forecast input reflects reality rather than a stale snapshot.
16. As a pharmacy owner, I want forecast persistence to be atomic, so that a generated forecast is either saved correctly or not saved at all.
17. As a pharmacy owner, I want forecast results to be tied to the correct location and DIN, so that reporting and review stay accurate.
18. As a backend operator, I want forecast calls logged with duration and status, so that operational issues can be diagnosed.
19. As a backend operator, I want forecast requests to time out predictably, so that the system does not hang on a stuck Python call.
20. As a backend operator, I want the SSE batch proxy not to buffer the full response, so that large batch runs stay responsive and memory-safe.
21. As a compliance officer, I want patient identifiers to remain excluded from external forecast calls, logs, and generated outputs, so that privacy obligations are preserved.
22. As a pharmacist, I want forecasts to include confidence and reorder status, so that I can prioritize the most urgent items first.
23. As a pharmacist, I want the forecast list sorted by the most recent and most urgent items by default, so that operational risk is visible immediately.
24. As a backend developer, I want clear module boundaries for the forecast client, orchestration service, controllers, and DTOs, so that the feature remains testable and maintainable.
25. As a backend developer, I want forecast upserts to use a real database conflict key, so that persistence is safe under retries and concurrent requests.
26. As a backend developer, I want the batch endpoint to persist each per-DIN result as it arrives, so that partial completion is not lost if the stream ends early.
27. As a backend developer, I want the same forecast layer to support future network-based supplemental history, so that later feature work can reuse the integration boundary.
28. As a pharmacist, I want forecast outputs to use Ontario/Canadian business defaults for lead time and safety posture, so that recommendations are operationally realistic.
29. As a pharmacist, I want forecasts to remain understandable even when the upstream service is unavailable, so that I can still see a clear error state.
30. As an owner, I want the backend to join forecast data with drug metadata and thresholds, so that the frontend does not need to stitch together multiple resources.

## Implementation Decisions

- Build the feature in the existing Spring package convention under `ca.pharmaforecast.backend`.
- Reuse the existing forecast-related JPA entities and repositories rather than creating a parallel domain model.
- Add a Spring `ForecastServiceClient` that wraps the Python forecast service using `RestClient`.
- Add a Spring `ForecastService` orchestration layer that owns effective stock calculation, forecast request assembly, persistence, read/query behavior, and supplemental cross-location history aggregation.
- Expose location-scoped REST endpoints under `/locations/{locationId}/forecasts`.
- Keep ownership validation server-side using the authenticated principal and the user’s organization membership.
- Use a 5-minute in-memory cache keyed by `locationId + din` to suppress duplicate generation requests.
- Use true SSE streaming for batch generation, proxied through Spring without buffering the full response.
- Add a forward Flyway migration to make `forecasts(location_id, din)` unique so that forecast persistence can use real `ON CONFLICT` upserts.
- Treat the Python forecast service as the source of truth for numeric forecasting, confidence, reorder status, and error semantics.
- Use `dispensing_records.quantity_on_hand` plus later `stock_adjustments` to calculate effective stock.
- Use `drug_thresholds` when available and default lead time and safety values when not.
- Join forecast reads with `drugs` for name and strength, and with `drug_thresholds` for threshold context.
- Support list filtering by horizon, reorder status, search, sort, and order.
- Return a clear unavailable result when the upstream forecast service times out or returns 4xx/5xx.
- Log forecast calls with `locationId`, `din`, `duration_ms`, and `status`.
- Preserve the compliance boundary by never sending `patient_id` outside the backend.
- Keep all forecast requests and responses location-bound and organization-bound.
- Use repository-backed persistence rather than writing directly from the Python service.
- Keep forecast confidence and reorder status aligned with the existing enum model and database constraints.
- Treat `GET /locations/{locationId}/forecasts` as a read API for the latest forecast per DIN, not a historical time-series endpoint.
- Default read ordering should prioritize recency and operational urgency: newest forecasts first, then more severe reorder status, then drug name.

## Testing Decisions

- Test the forecast client as a boundary around the Python service, not by mocking HTTP internals too deeply.
- Test forecast orchestration with fake repositories and fake client responses.
- Test ownership enforcement for location-scoped endpoints.
- Test effective stock calculation from dispensing records plus later stock adjustments.
- Test threshold fallback behavior when no custom threshold exists.
- Test upsert persistence behavior for repeated forecast saves.
- Test list queries for filtering, search, sort, and status aggregation.
- Test SSE batch proxy behavior at the event level.
- Test timeout and upstream error mapping to the unavailable forecast result.
- Test the duplicate-request cache behavior.
- Test supplemental history aggregation for multi-location fallback scenarios.
- Follow the codebase’s existing pattern of behavior-focused endpoint and service tests rather than implementation-detail tests.
- Add migration coverage for the new forecast uniqueness constraint and any schema changes needed to support upsert semantics.

## Out of Scope

- Python Prophet implementation changes.
- LLM/Grok explanations, chat, or purchase-order text generation.
- Automatic scheduled forecast generation beyond the integration layer described here.
- Database migration of the whole package root or any package naming refactor.
- Frontend UI implementation details beyond the API contract this backend exposes.
- Changes to auth bootstrap, Supabase JWT validation, or the existing security boundary.
- Patient-level analytics or any external export of patient identifiers.
- Long-term analytics dashboards or historical forecast trend reporting beyond latest forecast listing.
- External queueing infrastructure.
- Changing the existing `forecast_service` response contract.
- Replacing Supabase or changing the tenant model.

## Further Notes

- The existing schema already includes the core tables for forecasts, thresholds, dispensing, drugs, locations, and stock adjustments, so this feature is an integration and orchestration layer rather than a greenfield domain model.
- The current `forecasts` schema needs a unique conflict key before a safe upsert can be implemented.
- The Python forecast service is already defined as read-only and numeric-only; Spring Boot must remain the only service that persists forecast outputs.
- The forecast layer should preserve operational usefulness for pharmacists in Ontario by surfacing urgent reorder risk first and by using conservative defaults when threshold data is missing.
- Supplemental cross-location history is a forward-looking capability that should be implemented cleanly enough to support later network-level forecasting features.
- If you want, the next step can be turned into a concrete implementation plan with phases and file-level workstreams.
