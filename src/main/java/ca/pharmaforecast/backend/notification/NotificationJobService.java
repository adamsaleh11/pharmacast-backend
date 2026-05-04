package ca.pharmaforecast.backend.notification;

import ca.pharmaforecast.backend.auth.User;
import ca.pharmaforecast.backend.auth.UserRepository;
import ca.pharmaforecast.backend.auth.UserRole;
import ca.pharmaforecast.backend.currentstock.CurrentStockRepository;
import ca.pharmaforecast.backend.drug.Drug;
import ca.pharmaforecast.backend.drug.DrugRepository;
import ca.pharmaforecast.backend.forecast.DrugThreshold;
import ca.pharmaforecast.backend.forecast.DrugThresholdRepository;
import ca.pharmaforecast.backend.forecast.Forecast;
import ca.pharmaforecast.backend.forecast.ForecastRepository;
import ca.pharmaforecast.backend.forecast.ForecastServiceClient;
import ca.pharmaforecast.backend.forecast.NotificationCheckResult;
import ca.pharmaforecast.backend.forecast.ReorderStatus;
import ca.pharmaforecast.backend.insights.InsightsService;
import ca.pharmaforecast.backend.location.Location;
import ca.pharmaforecast.backend.location.LocationRepository;
import ca.pharmaforecast.backend.upload.CsvUploadRepository;
import ca.pharmaforecast.backend.upload.CsvUploadStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class NotificationJobService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationJobService.class);
    private static final ZoneId TORONTO = ZoneId.of("America/Toronto");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final LocationRepository locationRepository;
    private final ForecastServiceClient forecastServiceClient;
    private final NotificationRepository notificationRepository;
    private final NotificationSettingsRepository settingsRepository;
    private final DrugRepository drugRepository;
    private final DrugThresholdRepository thresholdRepository;
    private final CurrentStockRepository currentStockRepository;
    private final ForecastRepository forecastRepository;
    private final CsvUploadRepository csvUploadRepository;
    private final UserRepository userRepository;
    private final ResendEmailService emailService;
    private final InsightsService insightsService;
    private final Clock clock;
    private final String appUrl;

    public NotificationJobService(
            LocationRepository locationRepository,
            ForecastServiceClient forecastServiceClient,
            NotificationRepository notificationRepository,
            NotificationSettingsRepository settingsRepository,
            DrugRepository drugRepository,
            DrugThresholdRepository thresholdRepository,
            CurrentStockRepository currentStockRepository,
            ForecastRepository forecastRepository,
            CsvUploadRepository csvUploadRepository,
            UserRepository userRepository,
            ResendEmailService emailService,
            InsightsService insightsService,
            Clock clock,
            @Value("${pharmaforecast.app-url:${APP_URL:http://localhost:3000}}") String appUrl
    ) {
        this.locationRepository = locationRepository;
        this.forecastServiceClient = forecastServiceClient;
        this.notificationRepository = notificationRepository;
        this.settingsRepository = settingsRepository;
        this.drugRepository = drugRepository;
        this.thresholdRepository = thresholdRepository;
        this.currentStockRepository = currentStockRepository;
        this.forecastRepository = forecastRepository;
        this.csvUploadRepository = csvUploadRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.insightsService = insightsService;
        this.clock = clock;
        this.appUrl = appUrl == null || appUrl.isBlank() ? "http://localhost:3000" : appUrl.strip();
    }

    public void runDailyNotificationCheck() {
        for (Location location : locationRepository.findByDeactivatedAtIsNullOrderByNameAsc()) {
            NotificationCheckResult result = forecastServiceClient.runNotificationCheck(location.getId().toString());
            for (NotificationCheckResult.NotificationAlert alert : result.alerts()) {
                if ("RED".equalsIgnoreCase(alert.reorderStatus())) {
                    handleCriticalAlert(location, alert);
                }
            }
        }
    }

    public void sendDailyDigests() {
        for (Location location : locationRepository.findByDeactivatedAtIsNullOrderByNameAsc()) {
            if (!settings(location.getOrganizationId()).dailyDigestEnabled()) {
                continue;
            }
            List<Forecast> forecasts = latestForecasts(location).stream()
                    .filter(forecast -> forecast.getReorderStatus() == ReorderStatus.red || forecast.getReorderStatus() == ReorderStatus.amber)
                    .filter(forecast -> notificationsEnabled(location.getId(), forecast.getDin()))
                    .toList();
            String html = dailyDigestHtml(location, forecasts);
            String subject = "Your daily pharmacy forecast — " + LocalDate.now(clock.withZone(TORONTO));
            createNotification(location, NotificationType.daily_digest, Map.of(
                    "location_id", location.getId().toString(),
                    "date", LocalDate.now(clock.withZone(TORONTO)).toString(),
                    "alert_count", forecasts.size()
            ));
            sendToOperationalRecipients(location.getOrganizationId(), subject, html);
        }
    }

    public void sendWeeklyInsights() {
        LocalDate weekOf = LocalDate.now(clock.withZone(TORONTO));
        for (Location location : locationRepository.findByDeactivatedAtIsNullOrderByNameAsc()) {
            if (!settings(location.getOrganizationId()).weeklyInsightsEnabled()) {
                continue;
            }
            BigDecimal savings = insightsService.estimateMonthlySavings(location.getId()).orElse(BigDecimal.ZERO);
            String html = """
                    <h1>Your weekly pharmacy insights</h1>
                    <p>Estimated savings this week: $%s</p>
                    <p>Top demand changes are available in your insights dashboard.</p>
                    %s
                    <p><a href="%s/insights">Open insights</a></p>
                    """.formatted(
                    savings.setScale(2, RoundingMode.HALF_UP),
                    csvReminderHtml(location),
                    appUrl
            );
            createNotification(location, NotificationType.weekly_insight, Map.of(
                    "location_id", location.getId().toString(),
                    "week_of", weekOf.toString()
            ));
            sendToOperationalRecipients(location.getOrganizationId(), "Your weekly pharmacy insights — week of " + weekOf, html);
        }
    }

    private void handleCriticalAlert(Location location, NotificationCheckResult.NotificationAlert alert) {
        if (!notificationsEnabled(location.getId(), alert.din())) {
            return;
        }
        if (alreadySentToday(location, NotificationType.critical_reorder, alert.din())) {
            return;
        }
        Drug drug = drugRepository.findByDin(alert.din()).orElse(null);
        DrugThreshold threshold = thresholdRepository.findByLocationIdAndDin(location.getId(), alert.din()).orElse(null);
        int leadTime = threshold == null || threshold.getLeadTimeDays() == null ? 2 : threshold.getLeadTimeDays();
        int stock = currentStockRepository.findByLocationIdAndDin(location.getId(), alert.din())
                .map(currentStock -> Optional.ofNullable(currentStock.getQuantity()).orElse(0))
                .orElse(0);
        String drugName = drug == null ? alert.din() : drug.getName();
        String strength = drug == null ? "" : drug.getStrength();
        int predictedQuantity = Optional.ofNullable(alert.predictedQuantity()).orElse(0);
        double daysOfSupply = Optional.ofNullable(alert.daysOfSupply()).orElse(0.0);

        createNotification(location, NotificationType.critical_reorder, Map.of(
                "din", alert.din(),
                "drug_name", drugName,
                "strength", strength,
                "current_stock", stock,
                "days_of_supply", daysOfSupply,
                "forecasted_demand_7_days", predictedQuantity,
                "lead_time_days", leadTime,
                "date", LocalDate.now(clock.withZone(TORONTO)).toString()
        ));
        if (!settings(location.getOrganizationId()).criticalAlertsEnabled()) {
            return;
        }
        String subject = "Critical stock alert — %s at %s".formatted(drugName, location.getName());
        String html = criticalAlertHtml(alert.din(), drugName, strength, stock, daysOfSupply, predictedQuantity, leadTime);
        sendToOperationalRecipients(location.getOrganizationId(), subject, html);
    }

    private boolean alreadySentToday(Location location, NotificationType type, String din) {
        LocalDate today = LocalDate.now(clock.withZone(TORONTO));
        Instant start = today.atStartOfDay(TORONTO).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(TORONTO).toInstant();
        return notificationRepository.existsSameDayDinNotification(
                location.getOrganizationId(),
                location.getId(),
                type,
                din,
                start,
                end
        );
    }

    private boolean notificationsEnabled(UUID locationId, String din) {
        return thresholdRepository.findByLocationIdAndDin(locationId, din)
                .map(DrugThreshold::getNotificationsEnabled)
                .orElse(true);
    }

    private NotificationSettingsFlags settings(UUID organizationId) {
        return settingsRepository.findByOrganizationId(organizationId)
                .map(settings -> new NotificationSettingsFlags(
                        settings.getDailyDigestEnabled(),
                        settings.getWeeklyInsightsEnabled(),
                        settings.getCriticalAlertsEnabled()
                ))
                .orElse(new NotificationSettingsFlags(true, true, true));
    }

    private void createNotification(Location location, NotificationType type, Map<String, Object> payload) {
        Notification notification = Notification.create(
                location.getOrganizationId(),
                location.getId(),
                type,
                toJson(payload)
        );
        notification.setSentAt(Instant.now(clock));
        notificationRepository.save(notification);
    }

    private List<Forecast> latestForecasts(Location location) {
        Map<String, Forecast> byDin = new LinkedHashMap<>();
        for (Forecast forecast : forecastRepository.findByLocationIdOrderByGeneratedAtDesc(location.getId())) {
            byDin.putIfAbsent(forecast.getDin(), forecast);
        }
        return List.copyOf(byDin.values());
    }

    private String dailyDigestHtml(Location location, List<Forecast> forecasts) {
        if (forecasts.isEmpty()) {
            return """
                    <h1>Your daily pharmacy forecast</h1>
                    <p>All drugs well stocked today. Have a great day!</p>
                    %s
                    <p><a href="%s/dashboard">Open dashboard</a></p>
                    """.formatted(csvReminderHtml(location), appUrl);
        }
        StringBuilder red = new StringBuilder();
        StringBuilder amber = new StringBuilder();
        for (Forecast forecast : forecasts) {
            Drug drug = drugRepository.findByDin(forecast.getDin()).orElse(null);
            String name = drug == null ? forecast.getDin() : drug.getName();
            String line = "<li>%s — %.1f days remaining, order %d units</li>".formatted(
                    escapeHtml(name),
                    forecast.getDaysOfSupply() == null ? 0.0 : forecast.getDaysOfSupply().doubleValue(),
                    recommendedOrderQuantity(location.getId(), forecast)
            );
            if (forecast.getReorderStatus() == ReorderStatus.red) {
                red.append(line);
            } else {
                amber.append(line);
            }
        }
        return """
                <h1>Your daily pharmacy forecast</h1>
                <h2>Needs immediate attention</h2><ul>%s</ul>
                <h2>Reorder this week</h2><ul>%s</ul>
                <p>Summary: %d drugs need review today.</p>
                %s
                <p><a href="%s/dashboard">Open dashboard</a></p>
                """.formatted(red, amber, forecasts.size(), csvReminderHtml(location), appUrl);
    }

    private String criticalAlertHtml(String din, String drugName, String strength, int stock, double daysOfSupply, int demand, int leadTime) {
        return """
                <h1>Critical stock alert</h1>
                <p><strong>%s %s</strong></p>
                <p>Current stock: %d units</p>
                <p>Days of supply: %.1f days</p>
                <p>Forecasted demand next 7 days: %d units</p>
                <p>Recommended action: Place an order today — your supplier lead time is %d days</p>
                <p><a href="%s/dashboard?drug=%s">Open dashboard</a></p>
                """.formatted(escapeHtml(drugName), escapeHtml(strength), stock, daysOfSupply, demand, leadTime, appUrl, din);
    }

    private int recommendedOrderQuantity(UUID locationId, Forecast forecast) {
        int stock = currentStockRepository.findByLocationIdAndDin(locationId, forecast.getDin())
                .map(currentStock -> Optional.ofNullable(currentStock.getQuantity()).orElse(0))
                .orElse(0);
        return Math.max(0, Optional.ofNullable(forecast.getPredictedQuantity()).orElse(0) - stock);
    }

    private String csvReminderHtml(Location location) {
        Instant cutoff = Instant.now(clock).minus(java.time.Duration.ofDays(7));
        boolean overdue = csvUploadRepository.findTopByLocationIdAndStatusOrderByUploadedAtDesc(location.getId(), CsvUploadStatus.SUCCESS)
                .map(upload -> upload.getUploadedAt().isBefore(cutoff))
                .orElse(true);
        return overdue ? "<p><strong>CSV reminder:</strong> Upload a fresh dispensing CSV to keep forecasts current.</p>" : "";
    }

    private void sendToOperationalRecipients(UUID organizationId, String subject, String html) {
        List<User> recipients = userRepository.findByOrganizationIdAndRoleIn(organizationId, List.of(UserRole.owner, UserRole.admin));
        for (User recipient : recipients) {
            try {
                emailService.sendEmail(recipient.getEmail(), subject, html);
            } catch (RuntimeException ex) {
                LOGGER.error("Notification email send failed for organization {}", organizationId, ex);
            }
        }
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Notification payload could not be serialized", ex);
        }
    }

    private String escapeHtml(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private record NotificationSettingsFlags(
            boolean dailyDigestEnabled,
            boolean weeklyInsightsEnabled,
            boolean criticalAlertsEnabled
    ) {
    }
}
