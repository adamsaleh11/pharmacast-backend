package ca.pharmaforecast.backend.drug;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HealthCanadaSchedule(
        @JsonProperty("schedule_name") String scheduleName
) {
}
