package ca.pharmaforecast.backend.llm;

import java.time.Instant;

public record ExplanationResult(
        String explanation,
        Instant generatedAt,
        String error
) {
}
