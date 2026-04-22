package ca.pharmaforecast.backend.llm;

import ca.pharmaforecast.backend.auth.AuthenticatedUserPrincipal;
import ca.pharmaforecast.backend.auth.CurrentUserService;
import ca.pharmaforecast.backend.currentstock.CurrentStock;
import ca.pharmaforecast.backend.currentstock.CurrentStockRepository;
import ca.pharmaforecast.backend.dispensing.DispensingRecord;
import ca.pharmaforecast.backend.dispensing.DispensingRecordRepository;
import ca.pharmaforecast.backend.drug.Drug;
import ca.pharmaforecast.backend.drug.DrugRepository;
import ca.pharmaforecast.backend.forecast.Forecast;
import ca.pharmaforecast.backend.forecast.ForecastRepository;
import ca.pharmaforecast.backend.location.Location;
import ca.pharmaforecast.backend.location.LocationRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Transactional(readOnly = true)
public class ExplainService {

    private static final int CACHE_TTL_SECONDS = 3600;
    private static final int DEFAULT_LEAD_TIME_DAYS = 2;
    private static final int EXPLANATION_MAX_TOKENS = 600;

    private final CurrentUserService currentUserService;
    private final LocationRepository locationRepository;
    private final ForecastRepository forecastRepository;
    private final DrugRepository drugRepository;
    private final DispensingRecordRepository dispensingRecordRepository;
    private final CurrentStockRepository currentStockRepository;
    private final LlmServiceClient llmServiceClient;
    private final PayloadSanitizer payloadSanitizer;
    private final Clock clock;
    private final ConcurrentHashMap<UUID, CachedExplanation> explanationCache = new ConcurrentHashMap<>();

    public ExplainService(
            CurrentUserService currentUserService,
            LocationRepository locationRepository,
            ForecastRepository forecastRepository,
            DrugRepository drugRepository,
            DispensingRecordRepository dispensingRecordRepository,
            CurrentStockRepository currentStockRepository,
            LlmServiceClient llmServiceClient,
            PayloadSanitizer payloadSanitizer,
            Clock clock
    ) {
        this.currentUserService = currentUserService;
        this.locationRepository = locationRepository;
        this.forecastRepository = forecastRepository;
        this.drugRepository = drugRepository;
        this.dispensingRecordRepository = dispensingRecordRepository;
        this.currentStockRepository = currentStockRepository;
        this.llmServiceClient = llmServiceClient;
        this.payloadSanitizer = payloadSanitizer;
        this.clock = clock;
    }

    public ExplainResponse getExplanation(UUID locationId, String din) {
        AuthenticatedUserPrincipal currentUser = currentUserService.requireCurrentUser();
        validateOwnership(locationId, currentUser);

        Forecast forecast = forecastRepository.findTopByLocationIdAndDinOrderByGeneratedAtDesc(locationId, din)
                .orElseThrow(ForecastNotFoundException::new);
        Drug drug = drugRepository.findByDin(din)
                .orElseThrow(() -> new IllegalStateException("Drug not found for din " + din));
        CurrentStock currentStock = currentStockRepository.findByLocationIdAndDin(locationId, din)
                .orElseThrow(ExplainStockNotSetException::new);

        CachedExplanation cachedExplanation = explanationCache.get(forecast.getId());
        if (isFresh(cachedExplanation)) {
            return new ExplainResponse(cachedExplanation.explanation(), cachedExplanation.generatedAt());
        }

        ExplainPayload payload = new ExplainPayload(
                locationId,
                din,
                drug.getName(),
                drug.getStrength(),
                drug.getTherapeuticClass(),
                currentStock.getQuantity(),
                forecast.getDaysOfSupply(),
                forecast.getAvgDailyDemand(),
                forecast.getForecastHorizonDays(),
                forecast.getPredictedQuantity(),
                forecast.getProphetLower(),
                forecast.getProphetUpper(),
                forecast.getConfidence().name().toUpperCase(),
                forecast.getReorderStatus().name().toUpperCase(),
                forecast.getReorderPoint(),
                DEFAULT_LEAD_TIME_DAYS,
                forecast.getDataPointsUsed(),
                buildWeeklyQuantities(locationId, din, forecast.getGeneratedAt()),
                EXPLANATION_MAX_TOKENS
        );

        payloadSanitizer.sanitize(payload);

        ExplanationResult result = llmServiceClient.getExplanation(payload);
        if (result.error() != null) {
            throw new LlmUnavailableException();
        }

        CachedExplanation refreshed = new CachedExplanation(result.explanation(), result.generatedAt());
        explanationCache.put(forecast.getId(), refreshed);
        return new ExplainResponse(result.explanation(), result.generatedAt());
    }

    private boolean isFresh(CachedExplanation cachedExplanation) {
        if (cachedExplanation == null || cachedExplanation.generatedAt() == null) {
            return false;
        }
        Instant now = Instant.now(clock);
        return !cachedExplanation.generatedAt().plusSeconds(CACHE_TTL_SECONDS).isBefore(now);
    }

    private void validateOwnership(UUID locationId, AuthenticatedUserPrincipal currentUser) {
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new AccessDeniedException("Location is not accessible"));
        if (!location.getOrganizationId().equals(currentUser.organizationId())) {
            throw new AccessDeniedException("Location is not accessible");
        }
    }

    private List<Integer> buildWeeklyQuantities(UUID locationId, String din, Instant generatedAt) {
        LocalDate anchorDate = generatedAt.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate endWeekStart = anchorDate.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        LocalDate startWeekStart = endWeekStart.minusWeeks(7);

        Map<LocalDate, Integer> weeklyTotals = dispensingRecordRepository.findByLocationIdAndDin(locationId, din).stream()
                .filter(record -> record.getDispensedDate() != null)
                .filter(record -> !record.getDispensedDate().isBefore(startWeekStart))
                .filter(record -> !record.getDispensedDate().isAfter(anchorDate))
                .collect(java.util.stream.Collectors.groupingBy(
                        record -> record.getDispensedDate().with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)),
                        java.util.stream.Collectors.summingInt(DispensingRecord::getQuantityDispensed)
                ));

        List<Integer> weeklyQuantities = new ArrayList<>(8);
        for (int index = 0; index < 8; index++) {
            weeklyQuantities.add(weeklyTotals.getOrDefault(startWeekStart.plusWeeks(index), 0));
        }
        return weeklyQuantities;
    }

    record CachedExplanation(String explanation, Instant generatedAt) {
    }
}
