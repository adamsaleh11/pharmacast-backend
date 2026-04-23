package ca.pharmaforecast.backend.chat;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id,
        @JsonProperty("conversation_id")
        UUID conversationId,
        @JsonProperty("user_id")
        UUID userId,
        ChatRole role,
        String content,
        @JsonProperty("created_at")
        Instant createdAt
) {
}
