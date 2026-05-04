package ca.pharmaforecast.backend.insights;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HealthScoreResponse(
        int score,
        Breakdown breakdown
) {
    public record Breakdown(
            @JsonProperty("stock_health")
            int stockHealth,
            int accuracy,
            @JsonProperty("stockout_reduction")
            int stockoutReduction
    ) {
    }
}
