package ca.pharmaforecast.backend.llm;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ExplainPayload(
        @JsonProperty("location_id")
        UUID locationId,
        String din,
        @JsonProperty("drug_name")
        String drugName,
        String strength,
        @JsonProperty("therapeutic_class")
        String therapeuticClass,
        @JsonProperty("quantity_on_hand")
        int quantityOnHand,
        @JsonProperty("days_of_supply")
        BigDecimal daysOfSupply,
        @JsonProperty("avg_daily_demand")
        BigDecimal avgDailyDemand,
        @JsonProperty("horizon_days")
        int horizonDays,
        @JsonProperty("predicted_quantity")
        int predictedQuantity,
        @JsonProperty("prophet_lower")
        BigDecimal prophetLower,
        @JsonProperty("prophet_upper")
        BigDecimal prophetUpper,
        String confidence,
        @JsonProperty("reorder_status")
        String reorderStatus,
        @JsonProperty("reorder_point")
        BigDecimal reorderPoint,
        @JsonProperty("lead_time_days")
        int leadTimeDays,
        @JsonProperty("data_points_used")
        Integer dataPointsUsed,
        @JsonProperty("weekly_quantities")
        List<Integer> weeklyQuantities,
        @JsonProperty("max_tokens")
        int maxTokens
) {
}
