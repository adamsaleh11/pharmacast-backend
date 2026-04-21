package ca.pharmaforecast.backend.forecast;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ForecastRequest(
        @JsonProperty("location_id")
        String locationId,
        String din,
        @JsonProperty("horizon_days")
        Integer horizonDays,
        @JsonProperty("quantity_on_hand")
        Integer quantityOnHand,
        @JsonProperty("lead_time_days")
        Integer leadTimeDays,
        @JsonProperty("safety_multiplier")
        Double safetyMultiplier,
        @JsonProperty("red_threshold_days")
        Integer redThresholdDays,
        @JsonProperty("amber_threshold_days")
        Integer amberThresholdDays,
        @JsonProperty("supplemental_history")
        List<WeeklyQuantityDto> supplementalHistory
) {
}
