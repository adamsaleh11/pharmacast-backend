package ca.pharmaforecast.backend;

import ca.pharmaforecast.backend.auth.AuthenticatedUserPrincipal;
import ca.pharmaforecast.backend.auth.CurrentUserService;
import ca.pharmaforecast.backend.auth.UserRole;
import ca.pharmaforecast.backend.auth.User;
import ca.pharmaforecast.backend.chat.ChatContextBuilder;
import ca.pharmaforecast.backend.chat.ChatMessage;
import ca.pharmaforecast.backend.chat.ChatMessageRepository;
import ca.pharmaforecast.backend.chat.ChatRole;
import ca.pharmaforecast.backend.chat.ChatService;
import ca.pharmaforecast.backend.dispensing.DispensingRecordRepository;
import ca.pharmaforecast.backend.drug.DrugRepository;
import ca.pharmaforecast.backend.forecast.ForecastRepository;
import ca.pharmaforecast.backend.llm.ChatPayload;
import ca.pharmaforecast.backend.llm.LlmServiceClient;
import ca.pharmaforecast.backend.llm.PayloadSanitizer;
import ca.pharmaforecast.backend.location.Location;
import ca.pharmaforecast.backend.location.LocationRepository;
import ca.pharmaforecast.backend.notification.NotificationRepository;
import ca.pharmaforecast.backend.insights.InsightsService;
import ca.pharmaforecast.backend.organization.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        properties = {
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
        }
)
@AutoConfigureMockMvc
@Import(AuthTestRepositoryConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class ChatSendControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private LocationRepository locationRepository;

    @MockBean
    private ChatMessageRepository chatMessageRepository;

    @MockBean
    private ChatContextBuilder chatContextBuilder;

    @MockBean
    private OrganizationRepository organizationRepository;

    @MockBean
    private ForecastRepository forecastRepository;

    @MockBean
    private DrugRepository drugRepository;

    @MockBean
    private DispensingRecordRepository dispensingRecordRepository;

    @MockBean
    private NotificationRepository notificationRepository;

    @MockBean
    private LlmServiceClient llmServiceClient;

    @MockBean
    private PayloadSanitizer payloadSanitizer;

    @MockBean
    private InsightsService insightsService;

    @MockBean(name = "chatExecutor")
    private org.springframework.core.task.TaskExecutor chatExecutor;

    @BeforeEach
    void resetRepositories() {
        AuthTestRepositoryConfig.reset();
    }

    @Test
    void sendEndpointStreamsResponseAndPersistsConversationTurns() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        AuthTestRepositoryConfig.putUser(userId, "owner@example.com", user(userId, organizationId, "owner@example.com", UserRole.owner));
        AuthTestRepositoryConfig.putLocations(organizationId, List.of(location(locationId, organizationId)));

        when(currentUserService.requireCurrentUser()).thenReturn(new AuthenticatedUserPrincipal(
                userId,
                "owner@example.com",
                organizationId,
                UserRole.owner
        ));
        when(locationRepository.findById(locationId)).thenReturn(java.util.Optional.of(location(locationId, organizationId)));
        when(chatContextBuilder.buildSystemPrompt(locationId, organizationId)).thenReturn("system prompt");
        when(chatMessageRepository.findTop50ByLocationIdAndConversationIdAndUserIdOrderByCreatedAtDesc(eq(locationId), any(), eq(userId))).thenReturn(List.of(
                chatMessage(locationId, UUID.randomUUID(), userId, ChatRole.assistant, "Order now", Instant.parse("2026-04-21T09:59:00Z")),
                chatMessage(locationId, UUID.randomUUID(), userId, ChatRole.user, "Tell me about amoxicillin", Instant.parse("2026-04-21T09:58:00Z"))
        ));
        arrangeLlmStream("""
                data: {"token":"Hel"}

                data: {"token":"lo"}

                data: {"done":true,"total_tokens":2}
                """);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(chatExecutor).execute(any(Runnable.class));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String request = """
                {
                  "message": "What should I order?",
                  "conversation_id": "%s",
                  "conversation_history": []
                }
                """.formatted(conversationId);

        var result = mockMvc.perform(post("/locations/{locationId}/chat", locationId)
                        .with(jwt().jwt(token -> token
                                .subject(userId.toString())
                                .claim("email", "owner@example.com")
                                .audience(List.of("authenticated"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content(request))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(result))
                .andExpect(status().isOk());

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("data:{\"token\":\"Hel\"}");
        assertThat(body).contains("data:{\"token\":\"lo\"}");
        assertThat(body).contains("data:{\"done\":true,\"total_tokens\":2}");
        assertThat(body).doesNotContain("LLM_UNAVAILABLE");
        assertThat(body).doesNotContain("{token=");

        var payloadCaptor = org.mockito.ArgumentCaptor.forClass(ChatPayload.class);
        verify(payloadSanitizer).sanitize(payloadCaptor.capture());
        ChatPayload payload = payloadCaptor.getValue();
        assertThat(payload.systemPrompt()).isEqualTo("system prompt");
        assertThat(payload.messages()).hasSize(3);
        assertThat(payload.messages().get(0).get("content")).isEqualTo("Tell me about amoxicillin");
        assertThat(payload.messages().get(1).get("content")).isEqualTo("Order now");
        assertThat(payload.messages().get(2).get("content")).isEqualTo("What should I order?");

        var savedCaptor = org.mockito.ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, times(2)).save(savedCaptor.capture());
        List<ChatMessage> savedMessages = savedCaptor.getAllValues();
        assertThat(savedMessages).allSatisfy(message -> {
            assertThat(message.getConversationId()).isEqualTo(conversationId);
            assertThat(message.getUserId()).isEqualTo(userId);
        });
    }

    @Test
    void sendEndpointTrimsConversationHistoryToTwentyMessages() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        AuthTestRepositoryConfig.putUser(userId, "owner@example.com", user(userId, organizationId, "owner@example.com", UserRole.owner));
        AuthTestRepositoryConfig.putLocations(organizationId, List.of(location(locationId, organizationId)));

        when(currentUserService.requireCurrentUser()).thenReturn(new AuthenticatedUserPrincipal(
                userId,
                "owner@example.com",
                organizationId,
                UserRole.owner
        ));
        when(locationRepository.findById(locationId)).thenReturn(java.util.Optional.of(location(locationId, organizationId)));
        when(chatContextBuilder.buildSystemPrompt(locationId, organizationId)).thenReturn("system prompt");
        when(chatMessageRepository.findTop50ByLocationIdAndConversationIdAndUserIdOrderByCreatedAtDesc(eq(locationId), any(), eq(userId))).thenReturn(buildHistory(locationId, 25));
        arrangeLlmStream("""
                data: {"done":true,"total_tokens":1}
                """);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(chatExecutor).execute(any(Runnable.class));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StringBuilder history = new StringBuilder();
        history.append("[");
        for (int index = 1; index <= 25; index++) {
            if (index > 1) {
                history.append(",");
            }
            history.append("""
                    {"role":"user","content":"turn-%d"}
                    """.formatted(index).trim());
        }
        history.append("]");

        String request = """
                {
                  "message": "What should I order?",
                  "conversation_id": "%s",
                  "conversation_history": %s
                }
                """.formatted(conversationId, history);

        mockMvc.perform(post("/locations/{locationId}/chat", locationId)
                        .with(jwt().jwt(token -> token
                                .subject(userId.toString())
                                .claim("email", "owner@example.com")
                                .audience(List.of("authenticated"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content(request))
                .andExpect(request().asyncStarted());

        var payloadCaptor = org.mockito.ArgumentCaptor.forClass(ChatPayload.class);
        verify(payloadSanitizer).sanitize(payloadCaptor.capture());
        ChatPayload payload = payloadCaptor.getValue();
        assertThat(payload.messages()).hasSize(21);
        assertThat(payload.messages().get(0).get("content")).isEqualTo("turn-6");
        assertThat(payload.messages().get(19).get("content")).isEqualTo("turn-25");
        assertThat(payload.messages().get(20).get("content")).isEqualTo("What should I order?");
    }

    @Test
    void sendEndpointRejectsMessagesLongerThanTwoThousandCharacters() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        AuthTestRepositoryConfig.putUser(userId, "owner@example.com", user(userId, organizationId, "owner@example.com", UserRole.owner));
        AuthTestRepositoryConfig.putLocations(organizationId, List.of(location(locationId, organizationId)));

        String longMessage = "a".repeat(2001);
        String request = """
                {
                  "message": "%s",
                  "conversation_id": "%s",
                  "conversation_history": []
                }
                """.formatted(longMessage, conversationId);

        mockMvc.perform(post("/locations/{locationId}/chat", locationId)
                        .with(jwt().jwt(token -> token
                                .subject(userId.toString())
                                .claim("email", "owner@example.com")
                                .audience(List.of("authenticated"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sendEndpointPersistsPartialAssistantResponseWhenStreamFails() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        AuthTestRepositoryConfig.putUser(userId, "owner@example.com", user(userId, organizationId, "owner@example.com", UserRole.owner));
        AuthTestRepositoryConfig.putLocations(organizationId, List.of(location(locationId, organizationId)));

        when(currentUserService.requireCurrentUser()).thenReturn(new AuthenticatedUserPrincipal(
                userId,
                "owner@example.com",
                organizationId,
                UserRole.owner
        ));
        when(locationRepository.findById(locationId)).thenReturn(java.util.Optional.of(location(locationId, organizationId)));
        when(chatContextBuilder.buildSystemPrompt(locationId, organizationId)).thenReturn("system prompt");
        arrangeLlmStream(failingStream("""
                data: {"token":"Hel"}
                """));
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(chatExecutor).execute(any(Runnable.class));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String request = """
                {
                  "message": "What should I order?",
                  "conversation_id": "%s",
                  "conversation_history": []
                }
                """.formatted(conversationId);

        mockMvc.perform(post("/locations/{locationId}/chat", locationId)
                        .with(jwt().jwt(token -> token
                                .subject(userId.toString())
                                .claim("email", "owner@example.com")
                                .audience(List.of("authenticated"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content(request))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, times(2)).save(captor.capture());
        List<ChatMessage> savedMessages = captor.getAllValues();
        assertThat(savedMessages.get(1).isStreamError()).isTrue();
        assertThat(savedMessages.get(1).getContent()).isEqualTo("Hel");
    }

    @Test
    void sendEndpointEmitsSseErrorWhenDownstreamOpenFails() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        arrangeAuthenticatedLocation(userId, organizationId, locationId);
        when(chatContextBuilder.buildSystemPrompt(locationId, organizationId)).thenReturn("system prompt");
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("connection refused"))
                .when(llmServiceClient).streamChat(any(), any());
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(chatExecutor).execute(any(Runnable.class));

        String body = performChatRequest(locationId, userId);

        assertThat(body).contains("data:{\"error\":\"LLM_UNAVAILABLE\",\"message\":\"Try again in a moment\"}");
        assertThat(body).doesNotContain("\"done\":true");
    }

    @Test
    void sendEndpointEmitsOneSseErrorWhenDownstreamSendsErrorFrame(CapturedOutput output) throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        arrangeAuthenticatedLocation(userId, organizationId, locationId);
        when(chatContextBuilder.buildSystemPrompt(locationId, organizationId)).thenReturn("system prompt");
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        arrangeLlmStream("""
                data: {"error":"LLM_UNAVAILABLE","message":"Try again in a moment"}
                """);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(chatExecutor).execute(any(Runnable.class));

        String body = performChatRequest(locationId, userId);

        assertThat(body).contains("data:{\"error\":\"LLM_UNAVAILABLE\",\"message\":\"Try again in a moment\"}");
        assertThat(body.indexOf("\"error\":\"LLM_UNAVAILABLE\""))
                .isEqualTo(body.lastIndexOf("\"error\":\"LLM_UNAVAILABLE\""));
        assertThat(body).doesNotContain("\"done\":true");
        assertThat(output.getAll()).contains("chat_sse_error phase=read_downstream");
        assertThat(output.getAll()).contains("exception=DownstreamChatException");
    }

    @Test
    void sendEndpointEmitsSseErrorAndLogsParsePhaseWhenDownstreamDataIsMalformed(CapturedOutput output) throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        arrangeAuthenticatedLocation(userId, organizationId, locationId);
        when(chatContextBuilder.buildSystemPrompt(locationId, organizationId)).thenReturn("system prompt");
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        arrangeLlmStream("""
                data: {"token":"Hel"}

                data: {malformed-json}
                """);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(chatExecutor).execute(any(Runnable.class));

        String body = performChatRequest(locationId, userId);

        assertThat(body).contains("data:{\"token\":\"Hel\"}");
        assertThat(body).contains("data:{\"error\":\"LLM_UNAVAILABLE\",\"message\":\"Try again in a moment\"}");
        assertThat(output.getAll()).contains("chat_sse_error phase=parse_downstream");
        assertThat(output.getAll()).doesNotContain("malformed-json");
    }

    @Test
    void sendEndpointEmitsSseErrorAndLogsAssistantPersistencePhaseWhenAssistantSaveFails(CapturedOutput output) throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        arrangeAuthenticatedLocation(userId, organizationId, locationId);
        when(chatContextBuilder.buildSystemPrompt(locationId, organizationId)).thenReturn("system prompt");
        arrangeLlmStream("""
                data: {"token":"Hel"}

                data: {"done":true,"total_tokens":1}
                """);
        doAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            if (message.getRole() == ChatRole.assistant) {
                throw new RuntimeException("database unavailable");
            }
            return message;
        }).when(chatMessageRepository).save(any(ChatMessage.class));
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(chatExecutor).execute(any(Runnable.class));

        String body = performChatRequest(locationId, userId);

        assertThat(body).contains("data:{\"token\":\"Hel\"}");
        assertThat(body).contains("data:{\"error\":\"LLM_UNAVAILABLE\",\"message\":\"Try again in a moment\"}");
        assertThat(body).doesNotContain("\"done\":true");
        assertThat(output.getAll()).contains("chat_sse_error phase=persist_assistant");
        assertThat(output.getAll()).doesNotContain("database unavailable");
    }

    @Test
    void sendEndpointAcceptsCrlfDownstreamStream() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        arrangeAuthenticatedLocation(userId, organizationId, locationId);
        when(chatContextBuilder.buildSystemPrompt(locationId, organizationId)).thenReturn("system prompt");
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        arrangeLlmStream("data:{\"token\":\"Hel\"}\r\n\r\ndata:{\"done\":true,\"total_tokens\":1}\r\n\r\n");
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(chatExecutor).execute(any(Runnable.class));

        String body = performChatRequest(locationId, userId);

        assertThat(body).contains("data:{\"token\":\"Hel\"}");
        assertThat(body).contains("data:{\"done\":true,\"total_tokens\":1}");
        assertThat(body).doesNotContain("LLM_UNAVAILABLE");
    }

    private Location location(UUID id, UUID organizationId) throws Exception {
        Location location = org.springframework.util.ReflectionUtils.accessibleConstructor(Location.class).newInstance();
        ReflectionTestUtils.setField(location, "id", id);
        ReflectionTestUtils.setField(location, "organizationId", organizationId);
        ReflectionTestUtils.setField(location, "name", "Main Pharmacy");
        ReflectionTestUtils.setField(location, "address", "100 Bank St, Ottawa, ON");
        return location;
    }

    private User user(UUID id, UUID organizationId, String email, UserRole role) throws Exception {
        User user = org.springframework.util.ReflectionUtils.accessibleConstructor(User.class).newInstance();
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "organizationId", organizationId);
        ReflectionTestUtils.setField(user, "email", email);
        ReflectionTestUtils.setField(user, "role", role);
        return user;
    }

    private List<ChatMessage> buildHistory(UUID locationId, int count) throws Exception {
        List<ChatMessage> messages = new java.util.ArrayList<>();
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        for (int index = count; index >= 1; index--) {
            ChatRole role = index % 2 == 0 ? ChatRole.assistant : ChatRole.user;
            messages.add(chatMessage(
                    locationId,
                    conversationId,
                    userId,
                    role,
                    "turn-%d".formatted(index),
                    Instant.parse("2026-04-21T09:%02d:00Z".formatted(index))
            ));
        }
        return messages;
    }

    private void arrangeAuthenticatedLocation(UUID userId, UUID organizationId, UUID locationId) throws Exception {
        AuthTestRepositoryConfig.putUser(userId, "owner@example.com", user(userId, organizationId, "owner@example.com", UserRole.owner));
        AuthTestRepositoryConfig.putLocations(organizationId, List.of(location(locationId, organizationId)));
        when(currentUserService.requireCurrentUser()).thenReturn(new AuthenticatedUserPrincipal(
                userId,
                "owner@example.com",
                organizationId,
                UserRole.owner
        ));
        when(locationRepository.findById(locationId)).thenReturn(java.util.Optional.of(location(locationId, organizationId)));
        when(chatMessageRepository.findTop50ByLocationIdAndConversationIdAndUserIdOrderByCreatedAtDesc(eq(locationId), any(), eq(userId))).thenReturn(List.of());
    }

    private String performChatRequest(UUID locationId, UUID userId) throws Exception {
        UUID conversationId = UUID.randomUUID();
        String request = """
                {
                  "message": "What should I order?",
                  "conversation_id": "%s",
                  "conversation_history": []
                }
                """.formatted(conversationId);

        var result = mockMvc.perform(post("/locations/{locationId}/chat", locationId)
                        .with(jwt().jwt(token -> token
                                .subject(userId.toString())
                                .claim("email", "owner@example.com")
                                .audience(List.of("authenticated"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content(request))
                .andExpect(request().asyncStarted())
                .andReturn();

        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(result))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private ChatMessage chatMessage(UUID locationId, UUID conversationId, UUID userId, ChatRole role, String content, Instant createdAt) throws Exception {
        ChatMessage message = org.springframework.util.ReflectionUtils.accessibleConstructor(ChatMessage.class).newInstance();
        ReflectionTestUtils.setField(message, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(message, "locationId", locationId);
        ReflectionTestUtils.setField(message, "conversationId", conversationId);
        ReflectionTestUtils.setField(message, "userId", userId);
        ReflectionTestUtils.setField(message, "role", role);
        ReflectionTestUtils.setField(message, "content", content);
        ReflectionTestUtils.setField(message, "streamError", false);
        ReflectionTestUtils.setField(message, "createdAt", createdAt);
        return message;
    }

    private void arrangeLlmStream(String content) throws Exception {
        arrangeLlmStream(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    private void arrangeLlmStream(InputStream stream) throws Exception {
        doAnswer(invocation -> {
            LlmServiceClient.StreamHandler streamHandler = invocation.getArgument(1);
            streamHandler.handle(stream);
            return null;
        }).when(llmServiceClient).streamChat(any(), any());
    }

    private InputStream failingStream(String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return new InputStream() {
            private int index;

            @Override
            public int read() throws java.io.IOException {
                if (index < bytes.length) {
                    return bytes[index++] & 0xFF;
                }
                throw new java.io.IOException("boom");
            }
        };
    }
}
