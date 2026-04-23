package ca.pharmaforecast.backend.chat;

import ca.pharmaforecast.backend.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

import static lombok.AccessLevel.PROTECTED;

@Getter
@Setter
@Entity
@Table(name = "chat_messages")
@NoArgsConstructor(access = PROTECTED)
public class ChatMessage extends BaseEntity {

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private ChatRole role;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "stream_error", nullable = false)
    private boolean streamError;

    public UUID getLocationId() {
        return locationId;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public UUID getUserId() {
        return userId;
    }

    public ChatRole getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public boolean isStreamError() {
        return streamError;
    }

    public void setLocationId(UUID locationId) {
        this.locationId = locationId;
    }

    public void setConversationId(UUID conversationId) {
        this.conversationId = conversationId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public void setRole(ChatRole role) {
        this.role = role;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setStreamError(boolean streamError) {
        this.streamError = streamError;
    }
}
