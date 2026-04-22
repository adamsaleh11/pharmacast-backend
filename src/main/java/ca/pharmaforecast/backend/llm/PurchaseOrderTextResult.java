package ca.pharmaforecast.backend.llm;

import java.time.Instant;

public record PurchaseOrderTextResult(
        String orderText,
        Instant generatedAt,
        String error
) {
}
