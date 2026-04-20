package ca.pharmaforecast.backend.forecast;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NotificationCheckRequest(
        @JsonProperty("location_id")
        String locationId
) {
}
