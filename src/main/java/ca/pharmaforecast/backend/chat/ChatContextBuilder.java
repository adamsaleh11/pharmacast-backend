package ca.pharmaforecast.backend.chat;

import ca.pharmaforecast.backend.dispensing.DispensingRecord;
import ca.pharmaforecast.backend.dispensing.DispensingRecordRepository;
import ca.pharmaforecast.backend.drug.Drug;
import ca.pharmaforecast.backend.drug.DrugRepository;
import ca.pharmaforecast.backend.forecast.Forecast;
import ca.pharmaforecast.backend.forecast.ForecastRepository;
import ca.pharmaforecast.backend.insights.InsightsService;
import ca.pharmaforecast.backend.location.Location;
import ca.pharmaforecast.backend.location.LocationRepository;
import ca.pharmaforecast.backend.notification.Notification;
import ca.pharmaforecast.backend.notification.NotificationRepository;
import ca.pharmaforecast.backend.organization.Organization;
import ca.pharmaforecast.backend.organization.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ChatContextBuilder {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);
    private static final int FORECAST_CONTEXT_LIMIT = 20;

    private final OrganizationRepository organizationRepository;
    private final LocationRepository locationRepository;
    private final ForecastRepository forecastRepository;
    private final DrugRepository drugRepository;
    private final DispensingRecordRepository dispensingRecordRepository;
    private final NotificationRepository notificationRepository;
    private final InsightsService insightsService;
    private final Clock clock;

    public ChatContextBuilder(
            OrganizationRepository organizationRepository,
            LocationRepository locationRepository,
            ForecastRepository forecastRepository,
            DrugRepository drugRepository,
            DispensingRecordRepository dispensingRecordRepository,
            NotificationRepository notificationRepository,
            InsightsService insightsService,
            Clock clock
    ) {
        this.organizationRepository = organizationRepository;
        this.locationRepository = locationRepository;
        this.forecastRepository = forecastRepository;
        this.drugRepository = drugRepository;
        this.dispensingRecordRepository = dispensingRecordRepository;
        this.notificationRepository = notificationRepository;
        this.insightsService = insightsService;
        this.clock = clock;
    }

    public String buildSystemPrompt(UUID locationId, UUID organizationId) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new IllegalArgumentException("Location not found"));
        List<DispensingRecord> dispensingRecords = dispensingRecordRepository.findByLocationId(locationId);

        List<Forecast> latestForecasts = forecastRepository.findByLocationIdOrderByGeneratedAtDesc(locationId).stream()
                .collect(Collectors.groupingBy(Forecast::getDin, Collectors.collectingAndThen(
                        Collectors.maxBy(Comparator.comparing(Forecast::getGeneratedAt)),
                        forecast -> forecast.orElse(null)
                )))
                .values()
                .stream()
                .filter(forecast -> forecast != null)
                .sorted(Comparator
                        .comparing((Forecast forecast) -> severity(forecast.getReorderStatus()))
                        .thenComparing(Forecast::getDin))
                .toList();

        Map<String, Integer> volumeTotals = dispensingRecords.stream()
                .filter(record -> record.getDispensedDate() != null)
                .filter(record -> !record.getDispensedDate().isBefore(java.time.LocalDate.now(clock).minusDays(30)))
                .collect(Collectors.groupingBy(DispensingRecord::getDin, Collectors.summingInt(DispensingRecord::getQuantityDispensed)));

        Set<String> dinsToLoad = new java.util.HashSet<>(latestForecasts.stream().map(Forecast::getDin).toList());
        dinsToLoad.addAll(volumeTotals.keySet());

        Map<String, Drug> drugsByDin = drugRepository.findByDinIn(dinsToLoad.stream().toList())
                .stream()
                .collect(Collectors.toMap(Drug::getDin, Function.identity()));

        int redCount = (int) latestForecasts.stream().filter(forecast -> forecast.getReorderStatus().name().equalsIgnoreCase("red")).count();
        int amberCount = (int) latestForecasts.stream().filter(forecast -> forecast.getReorderStatus().name().equalsIgnoreCase("amber")).count();
        int greenCount = (int) latestForecasts.stream().filter(forecast -> forecast.getReorderStatus().name().equalsIgnoreCase("green")).count();
        Integer horizonDays = latestForecasts.stream().map(Forecast::getForecastHorizonDays).findFirst().orElse(0);

        List<String> drugLines = latestForecasts.stream()
                .limit(FORECAST_CONTEXT_LIMIT)
                .map(forecast -> formatForecastLine(forecast, drugsByDin.get(forecast.getDin())))
                .toList();

        List<String> topVolumeLines = topVolumeLines(volumeTotals, drugsByDin);
        List<String> alertLines = notificationRepository.findTop5ByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
                .map(this::formatNotificationLine)
                .toList();

        Optional<BigDecimal> savings = insightsService.estimateMonthlySavings(locationId);

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an AI pharmacy inventory advisor for ")
                .append(organization.getName())
                .append(",\n")
                .append("an independent pharmacy in Ottawa, Canada.\n")
                .append("You help the pharmacist make smart drug ordering decisions based on their\n")
                .append("dispensing data and AI demand forecasts.\n\n");

        prompt.append("CURRENT INVENTORY SNAPSHOT (")
                .append(location.getName())
                .append(", as of ")
                .append(DATE_FORMAT.format(clock.instant()))
                .append("):\n");
        prompt.append("Total drugs tracked: ").append(latestForecasts.size()).append('\n');
        prompt.append("Critical (reorder now): ").append(redCount).append(" drugs\n");
        prompt.append("Reorder soon: ").append(amberCount).append(" drugs\n");
        prompt.append("Well stocked: ").append(greenCount).append(" drugs\n\n");

        prompt.append("DRUG FORECAST SUMMARY (")
                .append(horizonDays)
                .append("-day horizon):\n");
        drugLines.forEach(line -> prompt.append(line).append('\n'));
        prompt.append('\n');

        prompt.append("TOP 10 DRUGS BY DISPENSING VOLUME (last 30 days):\n");
        topVolumeLines.forEach(line -> prompt.append(line).append('\n'));
        prompt.append('\n');

        prompt.append("RECENT ALERTS:\n");
        alertLines.forEach(line -> prompt.append(line).append('\n'));
        prompt.append('\n');

        savings.ifPresent(value -> prompt.append("ESTIMATED SAVINGS THIS MONTH: $")
                .append(value.setScale(2, RoundingMode.HALF_UP))
                .append('\n')
                .append('\n'));

        prompt.append("RULES:\n")
                .append("- Never reveal patient data, patient counts, or any patient-identifiable information\n")
                .append("- When recommending order quantities, reference the forecast data above\n")
                .append("- If asked about a drug not in the data, say it is not in their dispensing history\n")
                .append("- Respond concisely - pharmacists are busy\n")
                .append("- Format lists and order recommendations clearly\n")
                .append("- If forecast data is missing for a drug, suggest the pharmacist generate a forecast\n")
                .append("- Current stock quantities shown are pharmacist-entered values, not from the CSV\n");

        return prompt.toString();
    }

    private int severity(ca.pharmaforecast.backend.forecast.ReorderStatus status) {
        return switch (status) {
            case red -> 0;
            case amber -> 1;
            case green -> 2;
        };
    }

    private String formatForecastLine(Forecast forecast, Drug drug) {
        String name = drug == null ? forecast.getDin() : drug.getName();
        String strength = drug == null ? "" : " " + drug.getStrength();
        return "- %s%s: %d units needed, %s days remaining, status: %s".formatted(
                name,
                strength,
                forecast.getPredictedQuantity(),
                forecast.getDaysOfSupply() == null ? "n/a" : forecast.getDaysOfSupply().toPlainString(),
                forecast.getReorderStatus().name().toUpperCase(Locale.ROOT)
        );
    }

    private List<String> topVolumeLines(Map<String, Integer> volumeTotals, Map<String, Drug> drugsByDin) {
        return volumeTotals.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .map(entry -> {
                    Drug drug = drugsByDin.get(entry.getKey());
                    String name = drug == null ? entry.getKey() : drug.getName();
                    String strength = drug == null ? "" : " " + drug.getStrength();
                    return "- %s%s: %d units dispensed".formatted(name, strength, entry.getValue());
                })
                .toList();
    }

    private String formatNotificationLine(Notification notification) {
        return "- %s alert".formatted(notification.getType().name().toLowerCase(Locale.ROOT));
    }
}
