package ca.pharmaforecast.backend.llm;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record PurchaseOrderDrugPayload(
        @JsonProperty("drug_name")
        String drugName,
        String strength,
        String din,
        @JsonProperty("current_stock")
        int currentStock,
        @JsonProperty("predicted_quantity")
        int predictedQuantity,
        @JsonProperty("days_of_supply")
        BigDecimal daysOfSupply,
        @JsonProperty("reorder_status")
        String reorderStatus,
        @JsonProperty("avg_daily_demand")
        BigDecimal avgDailyDemand,
        @JsonProperty("lead_time_days")
        int leadTimeDays
) {
}
