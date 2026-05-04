package ca.pharmaforecast.backend.notification;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        @JsonProperty("organization_id") UUID organizationId,
        @JsonProperty("location_id") UUID locationId,
        NotificationType type,
        String payload,
        @JsonProperty("sent_at") Instant sentAt,
        @JsonProperty("read_at") Instant readAt,
        @JsonProperty("created_at") Instant createdAt
) {
}
