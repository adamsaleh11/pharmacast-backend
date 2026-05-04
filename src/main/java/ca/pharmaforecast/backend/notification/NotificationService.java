package ca.pharmaforecast.backend.notification;

import ca.pharmaforecast.backend.auth.AuthenticatedUserPrincipal;
import ca.pharmaforecast.backend.auth.CurrentUserService;
import ca.pharmaforecast.backend.auth.UserRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.List;

@Service
@Transactional
public class NotificationService {

    private final NotificationSettingsRepository settingsRepository;
    private final NotificationRepository notificationRepository;
    private final CurrentUserService currentUserService;

    public NotificationService(
            NotificationSettingsRepository settingsRepository,
            NotificationRepository notificationRepository,
            CurrentUserService currentUserService
    ) {
        this.settingsRepository = settingsRepository;
        this.notificationRepository = notificationRepository;
        this.currentUserService = currentUserService;
    }

    public NotificationSettingsResponse updateSettings(UUID organizationId, NotificationSettingsUpdateRequest request) {
        AuthenticatedUserPrincipal currentUser = requireSameOrganization(organizationId);
        if (currentUser.role() != UserRole.owner && currentUser.role() != UserRole.admin) {
            throw new AccessDeniedException("Notification settings are restricted to organization admins");
        }
        NotificationSettings settings = settingsRepository.findByOrganizationId(organizationId)
                .orElseGet(NotificationSettings::new);
        settings.setOrganizationId(organizationId);
        settings.setDailyDigestEnabled(request.dailyDigestEnabled());
        settings.setWeeklyInsightsEnabled(request.weeklyInsightsEnabled());
        settings.setCriticalAlertsEnabled(request.criticalAlertsEnabled());
        NotificationSettings saved = settingsRepository.save(settings);
        return new NotificationSettingsResponse(
                saved.getOrganizationId(),
                saved.getDailyDigestEnabled(),
                saved.getWeeklyInsightsEnabled(),
                saved.getCriticalAlertsEnabled()
        );
    }

    @Transactional(readOnly = true)
    public NotificationSettingsResponse getSettings(UUID organizationId) {
        requireSameOrganization(organizationId);
        return settingsRepository.findByOrganizationId(organizationId)
                .map(settings -> new NotificationSettingsResponse(
                        settings.getOrganizationId(),
                        settings.getDailyDigestEnabled(),
                        settings.getWeeklyInsightsEnabled(),
                        settings.getCriticalAlertsEnabled()
                ))
                .orElse(new NotificationSettingsResponse(
                        organizationId,
                        true,
                        true,
                        true
                ));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listNotifications(UUID organizationId, boolean unreadOnly) {
        requireSameOrganization(organizationId);
        List<Notification> notifications = unreadOnly
                ? notificationRepository.findTop30ByOrganizationIdAndReadAtIsNullOrderBySentAtDescCreatedAtDesc(organizationId)
                : notificationRepository.findTop30ByOrganizationIdOrderBySentAtDescCreatedAtDesc(organizationId);
        return notifications.stream()
                .map(this::toResponse)
                .toList();
    }

    public void markAllRead(UUID organizationId) {
        requireSameOrganization(organizationId);
        notificationRepository.markAllRead(organizationId);
    }

    private AuthenticatedUserPrincipal requireSameOrganization(UUID organizationId) {
        AuthenticatedUserPrincipal currentUser = currentUserService.requireCurrentUser();
        if (!currentUser.organizationId().equals(organizationId)) {
            throw new AccessDeniedException("Organization is not accessible");
        }
        return currentUser;
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getOrganizationId(),
                notification.getLocationId(),
                notification.getType(),
                notification.getPayload(),
                notification.getSentAt(),
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }
}
