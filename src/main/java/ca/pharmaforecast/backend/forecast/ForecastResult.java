package ca.pharmaforecast.backend.forecast;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ForecastResult(
        String din,
        @JsonProperty("location_id")
        String locationId,
        @JsonProperty("horizon_days")
        Integer horizonDays,
        @JsonProperty("predicted_quantity")
        Integer predictedQuantity,
        @JsonProperty("prophet_lower")
        Integer prophetLower,
        @JsonProperty("prophet_upper")
        Integer prophetUpper,
        String confidence,
        @JsonProperty("days_of_supply")
        Double daysOfSupply,
        @JsonProperty("avg_daily_demand")
        Double avgDailyDemand,
        @JsonProperty("reorder_status")
        String reorderStatus,
        @JsonProperty("reorder_point")
        Double reorderPoint,
        @JsonProperty("generated_at")
        String generatedAt,
        @JsonProperty("data_points_used")
        Integer dataPointsUsed
) {
}
