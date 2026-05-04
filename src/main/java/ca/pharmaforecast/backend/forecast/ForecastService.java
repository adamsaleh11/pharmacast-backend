package ca.pharmaforecast.backend.forecast;

import ca.pharmaforecast.backend.common.exception.InsufficientDataException;
import ca.pharmaforecast.backend.common.exception.NoStockEnteredException;
import ca.pharmaforecast.backend.forecast.InvalidForecastResultException;
import ca.pharmaforecast.backend.currentstock.CurrentStockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@Transactional
public class ForecastService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final java.util.Set<String> ALLOWED_MODEL_PATH_VALUES = java.util.Set.of(
            "xgboost_residual_interval",
            "fallback_recent_trend",
            "fallback_unsafe_xgboost_output",
            "prophet",
            "fallback_unsafe_prophet_output"
    );

    private final ForecastRequestAssembler forecastRequestAssembler;
    private final ForecastServiceClient forecastServiceClient;
    private final ForecastRepository forecastRepository;
    private final CurrentStockRepository currentStockRepository;

    public ForecastService(
            ForecastRequestAssembler forecastRequestAssembler,
            ForecastServiceClient forecastServiceClient,
            ForecastRepository forecastRepository,
            CurrentStockRepository currentStockRepository
    ) {
        this.forecastRequestAssembler = forecastRequestAssembler;
        this.forecastServiceClient = forecastServiceClient;
        this.forecastRepository = forecastRepository;
        this.currentStockRepository = currentStockRepository;
    }

    public ForecastResult generateForecast(UUID locationId, String din, Integer horizonDays) {
        ForecastRequest request = buildForecastRequest(locationId, din, horizonDays);
        ForecastResult result = forecastServiceClient.generateForecast(request);
        persistForecast(locationId, result);
        return result;
    }

    public ForecastRequest buildForecastRequest(UUID locationId, String din, Integer horizonDays) {
        return forecastRequestAssembler.build(locationId, din, horizonDays);
    }

    public Forecast persistForecast(UUID locationId, ForecastResult result) {
        if (result.dataPointsUsed() == null || result.dataPointsUsed() < 14) {
            throw new InsufficientDataException();
        }
        if (result.prophetLower() == null || result.prophetUpper() == null) {
            throw new InvalidForecastResultException(
                    "Forecast service returned an incomplete Prophet interval for din %s".formatted(result.din())
            );
        }
        if (result.prophetLower() > result.prophetUpper()) {
            throw new InvalidForecastResultException(
                    "Forecast service returned an invalid Prophet interval for din %s: lower=%d upper=%d"
                            .formatted(result.din(), result.prophetLower(), result.prophetUpper())
            );
        }
        if (result.modelPath() == null || result.modelPath().isBlank()) {
            throw new InvalidForecastResultException(
                    "Forecast service returned an empty model path for din %s".formatted(result.din())
            );
        }
        if (!ALLOWED_MODEL_PATH_VALUES.contains(result.modelPath())) {
            throw new InvalidForecastResultException(
                    "Forecast service returned an invalid model path for din %s: %s"
                            .formatted(result.din(), result.modelPath())
            );
        }

        Forecast forecast = forecastRepository.findByLocationIdAndDinAndForecastHorizonDays(
                locationId, result.din(), result.horizonDays()
        ).orElse(new Forecast());

        forecast.setLocationId(locationId);
        forecast.setDin(result.din());
        forecast.setGeneratedAt(Instant.parse(result.generatedAt()));
        forecast.setForecastHorizonDays(result.horizonDays());
        forecast.setPredictedQuantity(result.predictedQuantity());
        forecast.setConfidence(ForecastConfidence.valueOf(result.confidence().toLowerCase()));
        forecast.setDaysOfSupply(BigDecimal.valueOf(result.daysOfSupply()));
        forecast.setReorderStatus(ReorderStatus.valueOf(result.reorderStatus().toLowerCase()));
        forecast.setModelPath(result.modelPath());
        forecast.setProphetLower(BigDecimal.valueOf(result.prophetLower()));
        forecast.setProphetUpper(BigDecimal.valueOf(result.prophetUpper()));
        forecast.setAvgDailyDemand(BigDecimal.valueOf(result.avgDailyDemand()));
        forecast.setReorderPoint(BigDecimal.valueOf(result.reorderPoint()));
        forecast.setDataPointsUsed(result.dataPointsUsed());
        forecast.setIsOutdated(false);
        return forecastRepository.save(forecast);
    }

    public void streamBatchForecast(UUID locationId, BatchForecastRequest request, Consumer<ForecastBatchEvent> onEvent) {
        List<String> allDins = request.dins() == null ? List.of() : request.dins();
        java.util.Map<String, Integer> stockMap = currentStockRepository.getStockMapForLocation(locationId);
        List<String> dinsWithoutStock = allDins.stream().filter(din -> !stockMap.containsKey(din)).toList();
        List<String> dinsWithStock = allDins.stream().filter(stockMap::containsKey).toList();
        if (dinsWithStock.isEmpty()) {
            throw new NoStockEnteredException();
        }
        int total = allDins.size();
        int succeeded = 0;
        int failed = 0;
        BatchForecastRequest filteredRequest = new BatchForecastRequest(
                request.locationId(),
                dinsWithStock,
                request.horizonDays(),
                request.thresholds() == null ? Map.of() : request.thresholds().entrySet().stream()
                        .filter(entry -> stockMap.containsKey(entry.getKey()))
                        .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
        );
        InputStream stream = forecastServiceClient.generateBatchForecast(filteredRequest);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || !trimmed.startsWith("data:")) {
                    continue;
                }
                String payload = trimmed.substring("data:".length()).trim();
                JsonNode node = OBJECT_MAPPER.readTree(payload);
                if ("done".equalsIgnoreCase(text(node, "status")) || node.path("done").asBoolean(false)) {
                    onEvent.accept(new ForecastBatchEvent.Done(true, total, succeeded, failed, dinsWithoutStock.size(), dinsWithoutStock));
                    break;
                }
                String din = text(node, "din");
                if ("error".equalsIgnoreCase(text(node, "status"))) {
                    failed++;
                    onEvent.accept(new ForecastBatchEvent.Error(din, text(node, "error")));
                    continue;
                }
                try {
                    JsonNode resultNode = node.hasNonNull("result")
                            ? node.get("result")
                            : node.hasNonNull("forecast") ? node.get("forecast") : node;
                    ForecastResult result = OBJECT_MAPPER.treeToValue(resultNode, ForecastResult.class);
                    persistForecast(locationId, result);
                    succeeded++;
                    onEvent.accept(new ForecastBatchEvent.Result(din, result));
                } catch (Exception ex) {
                    failed++;
                    onEvent.accept(new ForecastBatchEvent.Error(din, forecastBatchErrorCode(ex)));
                }
            }
        } catch (IOException ex) {
            onEvent.accept(new ForecastBatchEvent.Error("", "FORECAST_UNAVAILABLE"));
            onEvent.accept(new ForecastBatchEvent.Done(true, total, succeeded, failed + 1, dinsWithoutStock.size(), dinsWithoutStock));
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }

    private String forecastBatchErrorCode(Exception ex) {
        if (ex instanceof InsufficientDataException) {
            return "INSUFFICIENT_DATA";
        }
        if (ex instanceof InvalidForecastResultException) {
            return "INVALID_FORECAST_RESULT";
        }
        return "FORECAST_UNAVAILABLE";
    }
}
