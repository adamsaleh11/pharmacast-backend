package ca.pharmaforecast.backend.notification;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/organizations/{orgId}")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PutMapping("/notification-settings")
    public NotificationSettingsResponse updateSettings(
            @PathVariable UUID orgId,
            @Valid @RequestBody NotificationSettingsUpdateRequest request
    ) {
        return notificationService.updateSettings(orgId, request);
    }

    @GetMapping("/notification-settings")
    public NotificationSettingsResponse getSettings(@PathVariable UUID orgId) {
        return notificationService.getSettings(orgId);
    }

    @GetMapping("/notifications")
    public List<NotificationResponse> listNotifications(
            @PathVariable UUID orgId,
            @RequestParam(defaultValue = "false") boolean unread
    ) {
        return notificationService.listNotifications(orgId, unread);
    }

    @PutMapping("/notifications/mark-all-read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@PathVariable UUID orgId) {
        notificationService.markAllRead(orgId);
    }
}
