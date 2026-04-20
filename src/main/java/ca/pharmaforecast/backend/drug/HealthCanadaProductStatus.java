package ca.pharmaforecast.backend.drug;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HealthCanadaProductStatus(
        String status,
        @JsonProperty("external_status_code") Integer externalStatusCode
) {
}
