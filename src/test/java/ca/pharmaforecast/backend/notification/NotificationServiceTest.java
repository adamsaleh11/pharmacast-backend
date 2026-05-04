package ca.pharmaforecast.backend.notification;

import ca.pharmaforecast.backend.auth.AuthenticatedUserPrincipal;
import ca.pharmaforecast.backend.auth.CurrentUserService;
import ca.pharmaforecast.backend.auth.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.List;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    private final NotificationSettingsRepository settingsRepository = mock(NotificationSettingsRepository.class);
    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private final NotificationService service = new NotificationService(settingsRepository, notificationRepository, currentUserService);

    @Test
    void updateSettingsUpsertsSettingsForAuthenticatedOrganization() {
        UUID organizationId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(currentUserService.requireCurrentUser()).thenReturn(new AuthenticatedUserPrincipal(
                UUID.randomUUID(),
                "owner@example.com",
                organizationId,
                UserRole.owner
        ));
        when(settingsRepository.findByOrganizationId(organizationId)).thenReturn(Optional.empty());
        when(settingsRepository.save(org.mockito.ArgumentMatchers.any(NotificationSettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationSettingsResponse response = service.updateSettings(
                organizationId,
                new NotificationSettingsUpdateRequest(false, true, false)
        );

        var captor = forClass(NotificationSettings.class);
        verify(settingsRepository).save(captor.capture());
        assertThat(captor.getValue().getOrganizationId()).isEqualTo(organizationId);
        assertThat(captor.getValue().getDailyDigestEnabled()).isFalse();
        assertThat(captor.getValue().getWeeklyInsightsEnabled()).isTrue();
        assertThat(captor.getValue().getCriticalAlertsEnabled()).isFalse();
        assertThat(response.organizationId()).isEqualTo(organizationId);
    }

    @Test
    void updateSettingsRejectsDifferentOrganization() {
        UUID routeOrganizationId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID userOrganizationId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(currentUserService.requireCurrentUser()).thenReturn(new AuthenticatedUserPrincipal(
                UUID.randomUUID(),
                "owner@example.com",
                userOrganizationId,
                UserRole.owner
        ));

        assertThatThrownBy(() -> service.updateSettings(
                routeOrganizationId,
                new NotificationSettingsUpdateRequest(true, true, true)
        )).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getSettingsReturnsSavedSettingsForAuthenticatedOrganization() {
        UUID organizationId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(currentUserService.requireCurrentUser()).thenReturn(new AuthenticatedUserPrincipal(
                UUID.randomUUID(),
                "staff@example.com",
                organizationId,
                UserRole.staff
        ));
        NotificationSettings settings = new NotificationSettings();
        settings.setOrganizationId(organizationId);
        settings.setDailyDigestEnabled(false);
        settings.setWeeklyInsightsEnabled(true);
        settings.setCriticalAlertsEnabled(false);
        when(settingsRepository.findByOrganizationId(organizationId)).thenReturn(Optional.of(settings));

        NotificationSettingsResponse response = service.getSettings(organizationId);

        assertThat(response.organizationId()).isEqualTo(organizationId);
        assertThat(response.dailyDigestEnabled()).isFalse();
        assertThat(response.weeklyInsightsEnabled()).isTrue();
        assertThat(response.criticalAlertsEnabled()).isFalse();
    }

    @Test
    void getSettingsDefaultsToEnabledWhenSettingsDoNotExist() {
        UUID organizationId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(currentUserService.requireCurrentUser()).thenReturn(new AuthenticatedUserPrincipal(
                UUID.randomUUID(),
                "staff@example.com",
                organizationId,
                UserRole.staff
        ));
        when(settingsRepository.findByOrganizationId(organizationId)).thenReturn(Optional.empty());

        NotificationSettingsResponse response = service.getSettings(organizationId);

        assertThat(response.organizationId()).isEqualTo(organizationId);
        assertThat(response.dailyDigestEnabled()).isTrue();
        assertThat(response.weeklyInsightsEnabled()).isTrue();
        assertThat(response.criticalAlertsEnabled()).isTrue();
    }

    @Test
    void listNotificationsReturnsUnreadNotificationsForAuthenticatedOrganization() {
        UUID organizationId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID locationId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        when(currentUserService.requireCurrentUser()).thenReturn(new AuthenticatedUserPrincipal(
                UUID.randomUUID(),
                "staff@example.com",
                organizationId,
                UserRole.staff
        ));
        Notification notification = Notification.create(
                organizationId,
                locationId,
                NotificationType.critical_reorder,
                "{\"din\":\"00012345\"}"
        );
        notification.setSentAt(Instant.parse("2026-04-24T11:00:00Z"));
        when(notificationRepository.findTop30ByOrganizationIdAndReadAtIsNullOrderBySentAtDescCreatedAtDesc(organizationId))
                .thenReturn(List.of(notification));

        List<NotificationResponse> responses = service.listNotifications(organizationId, true);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().organizationId()).isEqualTo(organizationId);
        assertThat(responses.getFirst().locationId()).isEqualTo(locationId);
        assertThat(responses.getFirst().type()).isEqualTo(NotificationType.critical_reorder);
        assertThat(responses.getFirst().payload()).contains("00012345");
    }

    @Test
    void markAllReadMarksUnreadNotificationsForAuthenticatedOrganization() {
        UUID organizationId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(currentUserService.requireCurrentUser()).thenReturn(new AuthenticatedUserPrincipal(
                UUID.randomUUID(),
                "staff@example.com",
                organizationId,
                UserRole.staff
        ));

        service.markAllRead(organizationId);

        verify(notificationRepository).markAllRead(organizationId);
    }
}
