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
