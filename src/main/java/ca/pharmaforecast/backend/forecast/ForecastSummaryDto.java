package ca.pharmaforecast.backend.forecast;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record ForecastSummaryDto(
        String din,
        @JsonProperty("drug_name")
        String drugName,
        String strength,
        @JsonProperty("predicted_quantity")
        Integer predictedQuantity,
        String confidence,
        @JsonProperty("days_of_supply")
        Double daysOfSupply,
        @JsonProperty("reorder_status")
        String reorderStatus,
        @JsonProperty("model_path")
        String modelPath,
        @JsonProperty("generated_at")
        Instant generatedAt,
        @JsonProperty("current_stock")
        Integer currentStock,
        @JsonProperty("stock_entered")
        boolean stockEntered,
        ForecastThresholdDto threshold,
        @JsonProperty("is_outdated")
        boolean isOutdated
) {
}
