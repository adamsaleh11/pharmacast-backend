package ca.pharmaforecast.backend.chat;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id,
        ChatRole role,
        String content,
        @JsonProperty("created_at")
        Instant createdAt
) {
}
