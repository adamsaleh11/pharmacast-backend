package ca.pharmaforecast.backend.insights;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record SavingsInsightsResponse(
        @JsonProperty("period_days")
        int periodDays,
        @JsonProperty("total_savings")
        BigDecimal totalSavings,
        @JsonProperty("overstock_avoided")
        OverstockAvoided overstockAvoided,
        @JsonProperty("waste_eliminated")
        WasteEliminated wasteEliminated,
        @JsonProperty("stockouts_prevented")
        StockoutsPrevented stockoutsPrevented,
        @JsonProperty("insufficient_data")
        boolean insufficientData,
        @JsonProperty("data_quality_message")
        String dataQualityMessage
) {
    public record OverstockAvoided(
            BigDecimal value,
            @JsonProperty("requires_cost_data")
            boolean requiresCostData
    ) {
    }

    public record WasteEliminated(
            BigDecimal value,
            @JsonProperty("requires_multiple_uploads")
            boolean requiresMultipleUploads
    ) {
    }

    public record StockoutsPrevented(
            int count,
            @JsonProperty("estimated_value")
            BigDecimal estimatedValue
    ) {
    }
}
