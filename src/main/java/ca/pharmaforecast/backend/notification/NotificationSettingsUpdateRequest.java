package ca.pharmaforecast.backend.notification;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record NotificationSettingsUpdateRequest(
        @NotNull @JsonProperty("daily_digest_enabled") Boolean dailyDigestEnabled,
        @NotNull @JsonProperty("weekly_insights_enabled") Boolean weeklyInsightsEnabled,
        @NotNull @JsonProperty("critical_alerts_enabled") Boolean criticalAlertsEnabled
) {
}
