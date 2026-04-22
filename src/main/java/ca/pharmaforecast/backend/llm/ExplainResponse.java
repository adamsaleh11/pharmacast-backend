package ca.pharmaforecast.backend.llm;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record ExplainResponse(
        String explanation,
        @JsonProperty("generated_at")
        Instant generatedAt
) {
}
