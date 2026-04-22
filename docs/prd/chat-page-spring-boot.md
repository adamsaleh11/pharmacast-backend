# Feature 9: Chat Page (Spring Boot)

## Problem Statement

Independent pharmacies need a fast, contextual inventory assistant that can answer questions about stock, demand, forecasts, and recent operational alerts without exposing patient-level data. Today, PharmaForecast already has the underlying LLM client and payload sanitization guardrails, but it does not yet expose a chat endpoint that combines live inventory context with persisted conversational history and streams the assistant response back to the frontend.

This matters now because the chat experience is meant to help pharmacists make ordering decisions in the moment. The assistant must be able to reason over current forecasts, dispensing volume, notifications, and savings context while preserving the product’s compliance boundary: no patient identifiers, no patient counts, no prescriber data, and no individual patient records can be sent to the LLM service.

## Solution

Build a Spring Boot chat feature that assembles a fresh inventory context for every user message, sends only aggregated and drug-level data to the llm_service, and streams the assistant’s response back to the browser as server-sent events.

The backend will expose a chat send endpoint and a chat history endpoint scoped to a location. For each chat message, the backend will verify location ownership, persist the user message, load the latest persisted turns for that location, build a new system prompt from live data, sanitize the payload, proxy the llm_service SSE stream to the client, and persist the assistant response after the stream completes. If the stream fails after partial output has been received, the backend will preserve the partial assistant text and mark the message as a failed stream so the conversation remains auditable.

The system prompt will be rebuilt from scratch on every message. It will reflect the current organization and location, the latest forecast summary, top drugs by dispensing volume, recent notifications, and an optional savings estimate when available.

## User Stories

1. As a pharmacist, I want to ask inventory questions in natural language, so that I can get quick ordering guidance without manually cross-referencing multiple dashboards.
2. As a pharmacist, I want the assistant to know my current location and organization, so that answers are specific to the store I am working in.
3. As a pharmacist, I want the assistant to use fresh forecast and dispensing data on every message, so that recommendations reflect the current state of inventory and demand.
4. As a pharmacist, I want the assistant to stream answers progressively, so that I can start reading useful content before the full response is complete.
5. As a pharmacist, I want my previous chat messages included in context, so that I can ask follow-up questions without restating everything.
6. As a pharmacist, I want the conversation history to remain bounded, so that responses stay fast and the backend does not send unnecessary tokens to the LLM service.
7. As a pharmacist, I want chat history to load for the current location only, so that I do not see conversations from another store.
8. As an organization owner, I want chat to respect tenant boundaries, so that one organization cannot access another organization’s inventory context or history.
9. As a security reviewer, I want the chat payload to exclude patient-level information, so that no patient-identifiable data can be sent to the external LLM service.
10. As a compliance reviewer, I want the assistant context to contain only aggregate drug-level information, so that PHIPA and PIPEDA constraints are preserved.
11. As a pharmacist, I want the assistant to understand recent alerts and notifications, so that it can answer in the context of current operational issues.
12. As a pharmacist, I want the assistant to mention savings when those estimates are available, so that I can prioritize high-value ordering changes.
13. As a pharmacist, I want the chat history to show what was actually asked and answered, so that I can review earlier recommendations later.
14. As a backend operator, I want failed streamed responses to keep partial content when possible, so that the conversation record is not lost on transient LLM failures.
15. As a backend operator, I want the assistant stream proxy to forward chunks as they arrive, so that the frontend can render the response incrementally.
16. As a backend developer, I want a dedicated context builder for chat, so that inventory prompt construction stays testable and separate from the controller.
17. As a backend developer, I want the chat context to be rebuilt on every request, so that prompt data is never stale or cached across messages.
18. As a backend developer, I want payload sanitization to run before every llm_service chat call, so that forbidden patient fields are rejected defensively.
19. As a frontend developer, I want a history endpoint that returns the latest 50 messages in order, so that I can render the current conversation timeline.
20. As a pharmacist, I want the assistant to tell me when a drug is not in the dispensing history, so that I can trust the limits of the data rather than receiving invented context.
21. As a pharmacist, I want the assistant to suggest generating a forecast when forecast data is missing, so that I know how to unlock a more precise recommendation.
22. As an operations reviewer, I want chat message persistence to capture both user and assistant turns, so that audit and support workflows can inspect the conversation after the fact.

## Implementation Decisions

- Add a dedicated read-only chat context builder service that assembles a fresh system prompt for every message.
- Add a chat controller with two endpoints: one to send a message and stream a response, and one to fetch chat history.
- Scope all chat reads and writes to a location, and validate that the location belongs to the authenticated user’s organization on every request.
- Reuse the existing current-user service and the same ownership validation pattern used by forecast and explanation endpoints.
- Load persisted chat history and trim it to the most recent 20 messages total before adding the current user message.
- Persist the user message before calling the LLM service so the conversation record is durable even if the downstream stream fails.
- Persist the assistant response after the SSE stream finishes, including partial text if the stream fails after some output has already been received.
- Add a boolean or equivalent stream-failure marker to assistant chat persistence so partial responses can be distinguished from completed ones.
- The chat context must include organization name, location name, the latest forecast summary grouped by DIN, per-drug forecast lines for up to 50 drugs sorted by worst reorder status first, top 10 drugs by dispensing volume over the last 30 days, the last 5 notifications for the organization, and an optional monthly savings estimate when available.
- The latest forecast summary must be derived from the latest forecast row per DIN at the current location.
- The per-drug forecast lines must be formatted as concise inventory recommendations, not as free-form narrative.
- Top drug volume calculations must use aggregated dispensing quantities only and must not include patient-level details.
- The system prompt must explicitly instruct the LLM to avoid patient data, patient counts, and patient-identifiable information.
- The chat endpoint must accept a message body with a maximum length of 2000 characters.
- The response must stream through server-sent events using the llm_service stream as the transport source rather than buffering the whole response first.
- The final SSE event must include a done marker and a best-effort token estimate.
- The chat history endpoint must return the last 50 messages for the location in ascending creation order.
- Chat persistence remains location-scoped in v1; there is no separate conversation or thread entity in this feature.
- Add a small read-only insights surface if needed so chat can optionally include a savings estimate without coupling to forecasting or LLM code.
- Use the existing `ChatPayload` contract and keep the `system_prompt` plus message list shape stable for llm_service.
- Continue to call the payload sanitizer defensively on the chat payload before the LLM request is made.
- The backend must not cache inventory context, prompt text, or chat history across requests.

## Testing Decisions

- Test the chat context builder as a contract: given seeded repository data, it should produce a prompt containing the expected organizational, forecast, volume, alert, and savings sections.
- Test that the chat context builder does not include patient identifiers, patient counts, prescriber data, or individual patient records in the generated prompt.
- Test that the chat endpoint rejects oversized messages and unauthorized location access.
- Test that sending a chat message persists the user turn before the LLM call and persists the assistant turn after a successful stream.
- Test that the chat endpoint proxies SSE chunks without buffering the entire response first.
- Test that a failed stream preserves partial assistant content and records the failure state.
- Test that the chat history endpoint returns the last 50 messages in ascending order.
- Test that the outbound chat payload is sanitized before the LLM client is invoked.
- Use existing LLM client tests and SSE controller patterns in the codebase as prior art for request-body contracts and streaming behavior.
- Use controller-level and service-level tests that verify external behavior rather than private helper methods.

## Out of Scope

- No frontend chat UI implementation.
- No standalone conversation/thread entity in v1.
- No RAG/vector search, embeddings, or semantic retrieval.
- No patient-level analytics, patient summaries, or prescriber intelligence.
- No direct Supabase client calls from the browser to the LLM service.
- No changes to forecast generation, CSV ingestion, or purchase order generation logic beyond the data they already expose to chat.
- No new external queue or background job for chat streaming.
- No prompt caching.
- No automatic message summarization or memory compaction beyond the bounded history window.

## Further Notes

- The assistant must remain inventory-focused, not clinical or prescribing-focused.
- The prompt should be rebuilt on every request so the model sees the current state of forecasts, alerts, and volume data.
- If savings cannot be calculated, the prompt should omit that section rather than inserting a placeholder or zero value.
- The chat feature depends on the existing llm_service SSE contract and on the availability of forecast, drug, dispensing, notification, and organization/location data.
- The data used in the prompt must remain Canada-resident and tenant-scoped.
- The implementation should keep chat persistence auditable enough to diagnose LLM failures without exposing forbidden patient data.
