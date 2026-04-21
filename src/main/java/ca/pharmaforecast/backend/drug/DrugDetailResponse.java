package ca.pharmaforecast.backend.drug;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record DrugDetailResponse(
        DrugDto drug,
        @JsonProperty("current_stock")
        Integer currentStock,
        @JsonProperty("stock_last_updated")
        Instant stockLastUpdated,
        @JsonProperty("latest_forecast")
        ForecastDto latestForecast,
        DrugThresholdDto threshold,
        @JsonProperty("dispensing_history")
        List<DispensingHistoryDto> dispensingHistory,
        @JsonProperty("stock_adjustments")
        List<StockAdjustmentDto> stockAdjustments
) {
    public record DrugDto(
            String din,
            String name,
            String strength,
            String form,
            @JsonProperty("therapeutic_class")
            String therapeuticClass,
            String manufacturer,
            String status
    ) {
    }

    public record ForecastDto(
            String din,
            @JsonProperty("generated_at")
            Instant generatedAt,
            @JsonProperty("forecast_horizon_days")
            Integer forecastHorizonDays,
            @JsonProperty("predicted_quantity")
            Integer predictedQuantity,
            String confidence,
            @JsonProperty("days_of_supply")
            Double daysOfSupply,
            @JsonProperty("reorder_status")
            String reorderStatus,
            @JsonProperty("model_path")
            String modelPath,
            @JsonProperty("prophet_lower")
            Double prophetLower,
            @JsonProperty("prophet_upper")
            Double prophetUpper,
            @JsonProperty("avg_daily_demand")
            Double avgDailyDemand,
            @JsonProperty("reorder_point")
            Double reorderPoint,
            @JsonProperty("data_points_used")
            Integer dataPointsUsed
    ) {
    }

    public record DrugThresholdDto(
            @JsonProperty("lead_time_days")
            Integer leadTimeDays,
            @JsonProperty("red_threshold_days")
            Integer redThresholdDays,
            @JsonProperty("amber_threshold_days")
            Integer amberThresholdDays,
            @JsonProperty("safety_multiplier")
            String safetyMultiplier,
            @JsonProperty("notifications_enabled")
            Boolean notificationsEnabled
    ) {
    }

    public record DispensingHistoryDto(
            LocalDate week,
            Integer quantity
    ) {
    }

    public record StockAdjustmentDto(
            @JsonProperty("adjusted_at")
            Instant adjustedAt,
            @JsonProperty("adjustment_quantity")
            Integer adjustmentQuantity,
            String note
    ) {
    }
}
