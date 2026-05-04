package ca.pharmaforecast.backend.notification;

import ca.pharmaforecast.backend.auth.User;
import ca.pharmaforecast.backend.auth.UserRepository;
import ca.pharmaforecast.backend.auth.UserRole;
import ca.pharmaforecast.backend.currentstock.CurrentStock;
import ca.pharmaforecast.backend.currentstock.CurrentStockRepository;
import ca.pharmaforecast.backend.drug.Drug;
import ca.pharmaforecast.backend.drug.DrugRepository;
import ca.pharmaforecast.backend.drug.DrugStatus;
import ca.pharmaforecast.backend.forecast.DrugThresholdRepository;
import ca.pharmaforecast.backend.forecast.ForecastRepository;
import ca.pharmaforecast.backend.forecast.ForecastServiceClient;
import ca.pharmaforecast.backend.forecast.NotificationCheckResult;
import ca.pharmaforecast.backend.insights.InsightsService;
import ca.pharmaforecast.backend.location.Location;
import ca.pharmaforecast.backend.location.LocationRepository;
import ca.pharmaforecast.backend.upload.CsvUploadRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.ReflectionUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationJobServiceTest {

    private final LocationRepository locationRepository = mock(LocationRepository.class);
    private final ForecastServiceClient forecastServiceClient = mock(ForecastServiceClient.class);
    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final NotificationSettingsRepository settingsRepository = mock(NotificationSettingsRepository.class);
    private final DrugRepository drugRepository = mock(DrugRepository.class);
    private final DrugThresholdRepository thresholdRepository = mock(DrugThresholdRepository.class);
    private final CurrentStockRepository currentStockRepository = mock(CurrentStockRepository.class);
    private final ForecastRepository forecastRepository = mock(ForecastRepository.class);
    private final CsvUploadRepository csvUploadRepository = mock(CsvUploadRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ResendEmailService emailService = mock(ResendEmailService.class);
    private final InsightsService insightsService = mock(InsightsService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-04-24T11:15:00Z"), ZoneOffset.UTC);
    private final NotificationJobService service = new NotificationJobService(
            locationRepository,
            forecastServiceClient,
            notificationRepository,
            settingsRepository,
            drugRepository,
            thresholdRepository,
            currentStockRepository,
            forecastRepository,
            csvUploadRepository,
            userRepository,
            emailService,
            insightsService,
            clock,
            "https://app.pharmaforecast.ca"
    );

    @Test
    void dailyNotificationCheckCreatesCriticalNotificationAndSendsEmailForRedAlert() throws Exception {
        UUID organizationId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID locationId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Location location = location(locationId, organizationId, "Downtown Pharmacy");
        Drug drug = drug("00012345", "Amoxicillin", "500 mg");
        CurrentStock currentStock = currentStock(locationId, "00012345", 4);
        User owner = user(organizationId, "owner@example.com", UserRole.owner);

        when(locationRepository.findByDeactivatedAtIsNullOrderByNameAsc()).thenReturn(List.of(location));
        when(forecastServiceClient.runNotificationCheck(locationId.toString())).thenReturn(new NotificationCheckResult(List.of(
                new NotificationCheckResult.NotificationAlert("00012345", "RED", 1.5, 18)
        )));
        when(notificationRepository.existsSameDayDinNotification(eq(organizationId), eq(locationId), eq(NotificationType.critical_reorder), eq("00012345"), any(), any()))
                .thenReturn(false);
        when(drugRepository.findByDin("00012345")).thenReturn(Optional.of(drug));
        when(thresholdRepository.findByLocationIdAndDin(locationId, "00012345")).thenReturn(Optional.empty());
        when(currentStockRepository.findByLocationIdAndDin(locationId, "00012345")).thenReturn(Optional.of(currentStock));
        when(settingsRepository.findByOrganizationId(organizationId)).thenReturn(Optional.empty());
        when(userRepository.findByOrganizationIdAndRoleIn(organizationId, List.of(UserRole.owner, UserRole.admin))).thenReturn(List.of(owner));

        service.runDailyNotificationCheck();

        var notificationCaptor = forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getType()).isEqualTo(NotificationType.critical_reorder);
        assertThat(notificationCaptor.getValue().getPayload()).contains("\"din\":\"00012345\"");
        assertThat(notificationCaptor.getValue().getSentAt()).isEqualTo(Instant.parse("2026-04-24T11:15:00Z"));
        verify(emailService).sendEmail(eq("owner@example.com"), contains("Critical stock alert"), contains("Amoxicillin"));
    }

    private Location location(UUID id, UUID organizationId, String name) throws Exception {
        Location location = ReflectionUtils.accessibleConstructor(Location.class).newInstance();
        ReflectionTestUtils.setField(location, "id", id);
        ReflectionTestUtils.setField(location, "organizationId", organizationId);
        ReflectionTestUtils.setField(location, "name", name);
        ReflectionTestUtils.setField(location, "address", "100 Bank St");
        return location;
    }

    private Drug drug(String din, String name, String strength) throws Exception {
        Drug drug = ReflectionUtils.accessibleConstructor(Drug.class).newInstance();
        drug.setDin(din);
        drug.setName(name);
        drug.setStrength(strength);
        drug.setForm("tablet");
        drug.setTherapeuticClass("antibiotic");
        drug.setManufacturer("Generic");
        drug.setStatus(DrugStatus.ACTIVE);
        drug.setLastRefreshedAt(Instant.parse("2026-04-20T00:00:00Z"));
        return drug;
    }

    private CurrentStock currentStock(UUID locationId, String din, int quantity) throws Exception {
        CurrentStock currentStock = ReflectionUtils.accessibleConstructor(CurrentStock.class).newInstance();
        currentStock.setLocationId(locationId);
        currentStock.setDin(din);
        currentStock.setQuantity(quantity);
        return currentStock;
    }

    private User user(UUID organizationId, String email, UserRole role) throws Exception {
        User user = ReflectionUtils.accessibleConstructor(User.class).newInstance();
        ReflectionTestUtils.setField(user, "organizationId", organizationId);
        ReflectionTestUtils.setField(user, "email", email);
        ReflectionTestUtils.setField(user, "role", role);
        return user;
    }
}
