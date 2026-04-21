package ca.pharmaforecast.backend.drug;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

public record DrugThresholdUpsertRequest(
        @JsonProperty("lead_time_days")
        @Min(1)
        @Max(30)
        Integer leadTimeDays,
        @JsonProperty("red_threshold_days")
        @Min(0)
        Integer redThresholdDays,
        @JsonProperty("amber_threshold_days")
        @Min(0)
        Integer amberThresholdDays,
        @JsonProperty("safety_multiplier")
        @Pattern(regexp = "CONSERVATIVE|BALANCED|AGGRESSIVE")
        String safetyMultiplier,
        @JsonProperty("notifications_enabled")
        Boolean notificationsEnabled
) {
    @AssertTrue(message = "red_threshold_days must be less than amber_threshold_days")
    public boolean isThresholdOrderingValid() {
        return redThresholdDays == null
                || amberThresholdDays == null
                || redThresholdDays < amberThresholdDays;
    }
}
