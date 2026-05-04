package ca.pharmaforecast.backend.insights;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AccuracyInsightsResponse(
        @JsonProperty("overall_accuracy_pct")
        Double overallAccuracyPct,
        @JsonProperty("by_drug")
        List<DrugAccuracy> byDrug
) {
    public record DrugAccuracy(
            String din,
            @JsonProperty("drug_name")
            String drugName,
            double mape,
            @JsonProperty("forecast_qty")
            int forecastQty,
            @JsonProperty("actual_qty")
            int actualQty
    ) {
    }
}
