package ca.pharmaforecast.backend.notification;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record NotificationSettingsResponse(
        @JsonProperty("organization_id") UUID organizationId,
        @JsonProperty("daily_digest_enabled") Boolean dailyDigestEnabled,
        @JsonProperty("weekly_insights_enabled") Boolean weeklyInsightsEnabled,
        @JsonProperty("critical_alerts_enabled") Boolean criticalAlertsEnabled
) {
}
