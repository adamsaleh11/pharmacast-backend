package ca.pharmaforecast.backend.purchaseorder;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PurchaseOrderPreviewResponse(
        @JsonProperty("generated_at")
        Instant generatedAt,
        @JsonProperty("order_text")
        String orderText,
        @JsonProperty("line_items")
        List<LineItem> lineItems
) {
    public record LineItem(
            String din,
            @JsonProperty("drug_name")
            String drugName,
            String strength,
            String form,
            @JsonProperty("current_stock")
            int currentStock,
            @JsonProperty("predicted_quantity")
            int predictedQuantity,
            @JsonProperty("recommended_quantity")
            int recommendedQuantity,
            @JsonProperty("days_of_supply")
            BigDecimal daysOfSupply,
            @JsonProperty("reorder_status")
            String reorderStatus,
            @JsonProperty("avg_daily_demand")
            BigDecimal avgDailyDemand,
            @JsonProperty("lead_time_days")
            int leadTimeDays,
            @JsonProperty("quantity_to_order")
            int quantityToOrder,
            String priority
    ) {
    }
}
