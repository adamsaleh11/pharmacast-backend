package ca.pharmaforecast.backend.chat;

import jakarta.validation.constraints.NotBlank;

public record ChatConversationMessage(
        @NotBlank String role,
        @NotBlank String content
) {
}
