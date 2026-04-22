package ca.pharmaforecast.backend.chat;

import ca.pharmaforecast.backend.auth.AuthenticatedUserPrincipal;
import ca.pharmaforecast.backend.auth.CurrentUserService;
import ca.pharmaforecast.backend.auth.UserRole;
import ca.pharmaforecast.backend.location.Location;
import ca.pharmaforecast.backend.location.LocationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatServiceTest {

    @Test
    void getHistoryReturnsLastFiftyMessagesInAscendingOrderForOwnedLocation() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();

        CurrentUserService currentUserService = mock(CurrentUserService.class);
        LocationRepository locationRepository = mock(LocationRepository.class);
        ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);

        when(currentUserService.requireCurrentUser()).thenReturn(new AuthenticatedUserPrincipal(
                userId,
                "owner@example.com",
                organizationId,
                UserRole.owner
        ));
        when(locationRepository.findById(locationId)).thenReturn(Optional.of(location(locationId, organizationId)));
        when(chatMessageRepository.findTop50ByLocationIdOrderByCreatedAtDesc(locationId)).thenReturn(List.of(
                chatMessage(locationId, ChatRole.assistant, "Second", Instant.parse("2026-04-21T10:01:00Z")),
                chatMessage(locationId, ChatRole.user, "First", Instant.parse("2026-04-21T10:00:00Z"))
        ));

        ChatService service = new ChatService(
                currentUserService,
                locationRepository,
                chatMessageRepository,
                mock(ChatContextBuilder.class),
                mock(ca.pharmaforecast.backend.organization.OrganizationRepository.class),
                mock(ca.pharmaforecast.backend.forecast.ForecastRepository.class),
                mock(ca.pharmaforecast.backend.drug.DrugRepository.class),
                mock(ca.pharmaforecast.backend.dispensing.DispensingRecordRepository.class),
                mock(ca.pharmaforecast.backend.notification.NotificationRepository.class),
                mock(ca.pharmaforecast.backend.llm.LlmServiceClient.class),
                mock(ca.pharmaforecast.backend.llm.PayloadSanitizer.class),
                mock(ca.pharmaforecast.backend.insights.InsightsService.class),
                mock(org.springframework.core.task.TaskExecutor.class)
        );

        List<ChatMessageResponse> history = service.getHistory(locationId);

        assertThat(history).extracting(ChatMessageResponse::content).containsExactly("First", "Second");
        assertThat(history).extracting(ChatMessageResponse::role).containsExactly(ChatRole.user, ChatRole.assistant);
        assertThat(history).hasSize(2);
    }

    @Test
    void getHistoryRejectsLocationsOutsideUsersOrganization() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID otherOrganizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();

        CurrentUserService currentUserService = mock(CurrentUserService.class);
        LocationRepository locationRepository = mock(LocationRepository.class);
        ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);

        when(currentUserService.requireCurrentUser()).thenReturn(new AuthenticatedUserPrincipal(
                userId,
                "owner@example.com",
                organizationId,
                UserRole.owner
        ));
        when(locationRepository.findById(locationId)).thenReturn(Optional.of(location(locationId, otherOrganizationId)));

        ChatService service = new ChatService(
                currentUserService,
                locationRepository,
                chatMessageRepository,
                mock(ChatContextBuilder.class),
                mock(ca.pharmaforecast.backend.organization.OrganizationRepository.class),
                mock(ca.pharmaforecast.backend.forecast.ForecastRepository.class),
                mock(ca.pharmaforecast.backend.drug.DrugRepository.class),
                mock(ca.pharmaforecast.backend.dispensing.DispensingRecordRepository.class),
                mock(ca.pharmaforecast.backend.notification.NotificationRepository.class),
                mock(ca.pharmaforecast.backend.llm.LlmServiceClient.class),
                mock(ca.pharmaforecast.backend.llm.PayloadSanitizer.class),
                mock(ca.pharmaforecast.backend.insights.InsightsService.class),
                mock(org.springframework.core.task.TaskExecutor.class)
        );

        assertThatThrownBy(() -> service.getHistory(locationId))
                .isInstanceOf(AccessDeniedException.class);
    }

    private Location location(UUID id, UUID organizationId) throws Exception {
        Location location = org.springframework.util.ReflectionUtils.accessibleConstructor(Location.class).newInstance();
        ReflectionTestUtils.setField(location, "id", id);
        ReflectionTestUtils.setField(location, "organizationId", organizationId);
        ReflectionTestUtils.setField(location, "name", "Main Pharmacy");
        ReflectionTestUtils.setField(location, "address", "100 Bank St, Ottawa, ON");
        return location;
    }

    private ChatMessage chatMessage(UUID locationId, ChatRole role, String content, Instant createdAt) throws Exception {
        ChatMessage message = org.springframework.util.ReflectionUtils.accessibleConstructor(ChatMessage.class).newInstance();
        ReflectionTestUtils.setField(message, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(message, "locationId", locationId);
        ReflectionTestUtils.setField(message, "role", role);
        ReflectionTestUtils.setField(message, "content", content);
        ReflectionTestUtils.setField(message, "createdAt", createdAt);
        return message;
    }
}
