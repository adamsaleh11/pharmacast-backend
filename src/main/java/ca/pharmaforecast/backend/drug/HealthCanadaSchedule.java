package ca.pharmaforecast.backend.drug;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HealthCanadaSchedule(
        @JsonProperty("schedule_name") String scheduleName
) {
}
