package ca.pharmaforecast.backend.forecast;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record BatchForecastRequest(
        @JsonProperty("location_id")
        String locationId,
        List<String> dins,
        @JsonProperty("horizon_days")
        Integer horizonDays,
        Map<String, ForecastThresholdDto> thresholds
) {
}
