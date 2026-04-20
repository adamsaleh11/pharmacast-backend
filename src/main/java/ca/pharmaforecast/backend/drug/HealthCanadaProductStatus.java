package ca.pharmaforecast.backend.drug;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HealthCanadaProductStatus(
        String status,
        @JsonProperty("external_status_code") Integer externalStatusCode
) {
}
