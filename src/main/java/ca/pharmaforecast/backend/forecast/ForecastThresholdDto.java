package ca.pharmaforecast.backend.forecast;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ForecastThresholdDto(
        @JsonProperty("lead_time_days")
        Integer leadTimeDays,
        @JsonProperty("safety_multiplier")
        Double safetyMultiplier
) {
}
