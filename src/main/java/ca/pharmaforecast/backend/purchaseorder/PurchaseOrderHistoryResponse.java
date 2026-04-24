package ca.pharmaforecast.backend.purchaseorder;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record PurchaseOrderHistoryResponse(
        UUID orderId,
        @JsonProperty("generated_at")
        Instant generatedAt,
        String status,
        @JsonProperty("item_count")
        int itemCount,
        @JsonProperty("total_units")
        int totalUnits
) {
}
