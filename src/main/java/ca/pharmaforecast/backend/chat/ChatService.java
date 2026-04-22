package ca.pharmaforecast.backend.chat;

import ca.pharmaforecast.backend.auth.AuthenticatedUserPrincipal;
import ca.pharmaforecast.backend.auth.CurrentUserService;
import ca.pharmaforecast.backend.dispensing.DispensingRecordRepository;
import ca.pharmaforecast.backend.drug.DrugRepository;
import ca.pharmaforecast.backend.forecast.ForecastRepository;
import ca.pharmaforecast.backend.insights.InsightsService;
import ca.pharmaforecast.backend.llm.ChatPayload;
import ca.pharmaforecast.backend.llm.LlmServiceClient;
import ca.pharmaforecast.backend.llm.PayloadSanitizer;
import ca.pharmaforecast.backend.location.Location;
import ca.pharmaforecast.backend.location.LocationRepository;
import ca.pharmaforecast.backend.notification.NotificationRepository;
import ca.pharmaforecast.backend.organization.OrganizationRepository;
import org.springframework.core.task.TaskExecutor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ChatService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String LLM_UNAVAILABLE = "LLM_UNAVAILABLE";
    private static final String LLM_UNAVAILABLE_MESSAGE = "Try again in a moment";

    private final CurrentUserService currentUserService;
    private final LocationRepository locationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatContextBuilder chatContextBuilder;
    private final OrganizationRepository organizationRepository;
    private final ForecastRepository forecastRepository;
    private final DrugRepository drugRepository;
    private final DispensingRecordRepository dispensingRecordRepository;
    private final NotificationRepository notificationRepository;
    private final LlmServiceClient llmServiceClient;
    private final PayloadSanitizer payloadSanitizer;
    private final InsightsService insightsService;
    private final TaskExecutor chatExecutor;

    public ChatService(
            CurrentUserService currentUserService,
            LocationRepository locationRepository,
            ChatMessageRepository chatMessageRepository,
            ChatContextBuilder chatContextBuilder,
            OrganizationRepository organizationRepository,
            ForecastRepository forecastRepository,
            DrugRepository drugRepository,
            DispensingRecordRepository dispensingRecordRepository,
            NotificationRepository notificationRepository,
            LlmServiceClient llmServiceClient,
            PayloadSanitizer payloadSanitizer,
            InsightsService insightsService,
            TaskExecutor chatExecutor
    ) {
        this.currentUserService = currentUserService;
        this.locationRepository = locationRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatContextBuilder = chatContextBuilder;
        this.organizationRepository = organizationRepository;
        this.forecastRepository = forecastRepository;
        this.drugRepository = drugRepository;
        this.dispensingRecordRepository = dispensingRecordRepository;
        this.notificationRepository = notificationRepository;
        this.llmServiceClient = llmServiceClient;
        this.payloadSanitizer = payloadSanitizer;
        this.insightsService = insightsService;
        this.chatExecutor = chatExecutor;
    }

    public List<ChatMessageResponse> getHistory(UUID locationId) {
        AuthenticatedUserPrincipal currentUser = currentUserService.requireCurrentUser();
        validateLocationOwnership(locationId, currentUser);

        return chatMessageRepository.findTop50ByLocationIdOrderByCreatedAtDesc(locationId).stream()
                .sorted(java.util.Comparator.comparing(ChatMessage::getCreatedAt))
                .map(message -> new ChatMessageResponse(
                        message.getId(),
                        message.getRole(),
                        message.getContent(),
                        message.getCreatedAt()
                ))
                .toList();
    }

    @Transactional
    public SseEmitter sendMessage(UUID locationId, ChatSendRequest request) {
        AuthenticatedUserPrincipal currentUser = currentUserService.requireCurrentUser();
        validateLocationOwnership(locationId, currentUser);

        UUID requestId = UUID.randomUUID();
        LOGGER.info("chat_sse_start location_id={} request_id={}", locationId, requestId);

        List<ChatMessage> persistedHistory = loadConversationHistory(locationId);

        ChatMessage userMessage = new ChatMessage();
        userMessage.setLocationId(locationId);
        userMessage.setRole(ChatRole.user);
        userMessage.setContent(request.message());
        userMessage.setStreamError(false);
        chatMessageRepository.save(userMessage);

        String phase = "build_payload";
        String systemPrompt = chatContextBuilder.buildSystemPrompt(locationId, currentUser.organizationId());
        ChatPayload payload = new ChatPayload(systemPrompt, buildOutboundMessages(persistedHistory, request.message()));
        LOGGER.info(
                "chat_payload_ready system_chars={} message_count={} location_id={} request_id={}",
                systemPrompt.length(),
                payload.messages().size(),
                locationId,
                requestId
        );
        phase = "sanitize_payload";
        payloadSanitizer.sanitize(payload);

        SseEmitter emitter = new SseEmitter(0L);
        String completedPhase = phase;
        chatExecutor.execute(() -> streamConversation(locationId, requestId, emitter, payload, completedPhase));
        return emitter;
    }

    private void streamConversation(UUID locationId, UUID requestId, SseEmitter emitter, ChatPayload payload, String initialPhase) {
        StringBuilder assistantResponse = new StringBuilder();
        Integer totalTokens = null;
        String[] phase = {initialPhase};
        boolean emittedTerminal = false;
        try {
            phase[0] = "open_downstream";
            final Integer[] downstreamTotalTokens = {null};
            llmServiceClient.streamChat(payload, stream -> {
                LOGGER.info("chat_sse_downstream_open success=true location_id={} request_id={}", locationId, requestId);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while (true) {
                        phase[0] = "read_downstream";
                        line = reader.readLine();
                        if (line == null) {
                            break;
                        }
                        String trimmed = line.trim();
                        if (!trimmed.startsWith("data:")) {
                            continue;
                        }
                        String json = trimmed.substring("data:".length()).trim();
                        if (json.isBlank()) {
                            continue;
                        }

                        phase[0] = "parse_downstream";
                        JsonNode node = OBJECT_MAPPER.readTree(json);
                        if (node.path("error").isTextual()) {
                            phase[0] = "read_downstream";
                            throw new DownstreamChatException();
                        }
                        if (node.path("token").isTextual()) {
                            String token = node.get("token").asText();
                            LOGGER.info(
                                    "chat_sse_downstream_frame kind=token token_length={} location_id={} request_id={}",
                                    token.length(),
                                    locationId,
                                    requestId
                            );
                            assistantResponse.append(token);
                            phase[0] = "emit_token";
                            emitToken(emitter, token);
                            LOGGER.info(
                                    "chat_sse_emit kind=token token_length={} location_id={} request_id={}",
                                    token.length(),
                                    locationId,
                                    requestId
                            );
                            continue;
                        }
                        if (node.path("done").asBoolean(false)) {
                            downstreamTotalTokens[0] = node.hasNonNull("total_tokens") ? node.get("total_tokens").asInt() : null;
                            LOGGER.info(
                                    "chat_sse_downstream_frame kind=done total_tokens={} location_id={} request_id={}",
                                    downstreamTotalTokens[0],
                                    locationId,
                                    requestId
                            );
                        }
                    }
                }
            });
            totalTokens = downstreamTotalTokens[0];

            phase[0] = "persist_assistant";
            persistAssistantMessage(locationId, assistantResponse.toString(), false);
            int emittedTotalTokens = totalTokens == null ? estimateTokens(assistantResponse.toString()) : totalTokens;
            phase[0] = "emit_done";
            emitDone(emitter, emittedTotalTokens);
            emittedTerminal = true;
            LOGGER.info(
                    "chat_sse_emit kind=done total_tokens={} location_id={} request_id={}",
                    emittedTotalTokens,
                    locationId,
                    requestId
            );
            phase[0] = "complete_emitter";
            emitter.complete();
            LOGGER.info(
                    "chat_sse_complete emitted_terminal=true assistant_chars={} location_id={} request_id={}",
                    assistantResponse.length(),
                    locationId,
                    requestId
            );
        } catch (Exception ex) {
            LOGGER.warn(
                    "chat_sse_error phase={} exception={} safe_message={} location_id={} request_id={}",
                    phase[0],
                    exceptionClass(ex),
                    safeMessage(phase[0]),
                    locationId,
                    requestId
            );
            if (assistantResponse.length() > 0) {
                try {
                    persistAssistantMessage(locationId, assistantResponse.toString(), true);
                } catch (Exception persistEx) {
                    LOGGER.warn(
                            "chat_sse_error phase=persist_assistant exception={} safe_message={} location_id={} request_id={}",
                            persistEx.getClass().getSimpleName(),
                            safeMessage("persist_assistant"),
                            locationId,
                            requestId
                    );
                }
            }
            if (!emittedTerminal) {
                try {
                    emitError(emitter);
                    emittedTerminal = true;
                    LOGGER.info(
                            "chat_sse_emit kind=error code={} message_length={} location_id={} request_id={}",
                            LLM_UNAVAILABLE,
                            LLM_UNAVAILABLE_MESSAGE.length(),
                            locationId,
                            requestId
                    );
                    emitter.complete();
                    LOGGER.info(
                            "chat_sse_complete emitted_terminal=true assistant_chars={} location_id={} request_id={}",
                            assistantResponse.length(),
                            locationId,
                            requestId
                    );
                } catch (IOException emitEx) {
                    LOGGER.warn(
                            "chat_sse_error phase=emit_error exception={} safe_message={} location_id={} request_id={}",
                            emitEx.getClass().getSimpleName(),
                            safeMessage("emit_error"),
                            locationId,
                            requestId
                    );
                    emitter.completeWithError(ex);
                }
            }
        }
    }

    private void emitToken(SseEmitter emitter, String token) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("token", token);
        emitter.send(SseEmitter.event().data(OBJECT_MAPPER.writeValueAsString(payload)));
    }

    private void emitDone(SseEmitter emitter, int totalTokens) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("done", true);
        payload.put("total_tokens", totalTokens);
        emitter.send(SseEmitter.event().data(OBJECT_MAPPER.writeValueAsString(payload)));
    }

    private void emitError(SseEmitter emitter) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("error", LLM_UNAVAILABLE);
        payload.put("message", LLM_UNAVAILABLE_MESSAGE);
        emitter.send(SseEmitter.event().data(OBJECT_MAPPER.writeValueAsString(payload)));
    }

    private String safeMessage(String phase) {
        return switch (phase) {
            case "parse_downstream" -> "Malformed downstream SSE data";
            case "persist_assistant" -> "Assistant message persistence failed";
            case "open_downstream" -> "Downstream stream unavailable";
            case "read_downstream" -> "Downstream stream read failed";
            case "emit_token", "emit_done", "emit_error" -> "SSE emit failed";
            case "complete_emitter" -> "SSE completion failed";
            default -> "Chat stream failed";
        };
    }

    private String exceptionClass(Exception ex) {
        Throwable cause = ex.getCause();
        if ("StreamHandlingException".equals(ex.getClass().getSimpleName()) && cause != null) {
            return cause.getClass().getSimpleName();
        }
        return ex.getClass().getSimpleName();
    }

    private static class DownstreamChatException extends RuntimeException {
    }

    private void persistAssistantMessage(UUID locationId, String content, boolean streamError) {
        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setLocationId(locationId);
        assistantMessage.setRole(ChatRole.assistant);
        assistantMessage.setContent(content);
        assistantMessage.setStreamError(streamError);
        chatMessageRepository.save(assistantMessage);
    }

    private List<ChatMessage> loadConversationHistory(UUID locationId) {
        return chatMessageRepository.findTop50ByLocationIdOrderByCreatedAtDesc(locationId).stream()
                .filter(message -> message.getRole() == ChatRole.user || message.getRole() == ChatRole.assistant)
                .limit(20)
                .sorted(java.util.Comparator.comparing(ChatMessage::getCreatedAt))
                .toList();
    }

    private List<Map<String, String>> buildOutboundMessages(List<ChatMessage> persistedHistory, String message) {
        List<Map<String, String>> messages = new ArrayList<>();
        for (ChatMessage chatMessage : persistedHistory) {
            messages.add(Map.of(
                    "role", chatMessage.getRole().name(),
                    "content", chatMessage.getContent()
            ));
        }
        messages.add(Map.of("role", ChatRole.user.name(), "content", message));
        return messages;
    }

    private int estimateTokens(String content) {
        if (!StringUtils.hasText(content)) {
            return 0;
        }
        return Math.max(1, (content.length() + 3) / 4);
    }

    private void validateLocationOwnership(UUID locationId, AuthenticatedUserPrincipal currentUser) {
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new AccessDeniedException("Location is not accessible"));
        if (!location.getOrganizationId().equals(currentUser.organizationId())) {
            throw new AccessDeniedException("Location is not accessible");
        }
    }
}
