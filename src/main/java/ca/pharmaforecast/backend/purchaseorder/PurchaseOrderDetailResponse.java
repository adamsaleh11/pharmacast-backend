package ca.pharmaforecast.backend.purchaseorder;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PurchaseOrderDetailResponse(
        @JsonProperty("orderId")
        UUID orderId,
        @JsonProperty("generated_at")
        Instant generatedAt,
        @JsonProperty("order_text")
        String orderText,
        @JsonProperty("line_items")
        List<PurchaseOrderPreviewResponse.LineItem> lineItems
) {
}
