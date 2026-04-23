package ca.pharmaforecast.backend.chat;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/locations/{locationId}/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/history")
    public List<ChatMessageResponse> history(@PathVariable UUID locationId) {
        return chatService.getHistory(locationId);
    }

    @GetMapping("/conversations")
    public List<ChatConversationResponse> conversations(@PathVariable UUID locationId) {
        return chatService.listConversations(locationId);
    }

    @GetMapping("/{conversationId}/history")
    public List<ChatMessageResponse> conversationHistory(
            @PathVariable UUID locationId,
            @PathVariable UUID conversationId
    ) {
        return chatService.getHistory(locationId, conversationId);
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> send(
            @PathVariable UUID locationId,
            @Valid @RequestBody ChatSendRequest request
    ) {
        ChatService.ChatSendResult result = chatService.sendMessage(locationId, request);
        return ResponseEntity.ok()
                .header("X-Conversation-Id", result.conversationId().toString())
                .body(result.emitter());
    }
}
