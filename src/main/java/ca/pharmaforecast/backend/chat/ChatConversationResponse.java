package ca.pharmaforecast.backend.chat;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record ChatConversationResponse(
        @JsonProperty("conversation_id")
        UUID conversationId,
        @JsonProperty("location_id")
        UUID locationId,
        @JsonProperty("user_id")
        UUID userId,
        @JsonProperty("started_at")
        Instant startedAt,
        @JsonProperty("last_message_at")
        Instant lastMessageAt,
        @JsonProperty("message_count")
        long messageCount
) {
}
