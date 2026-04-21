package ca.pharmaforecast.backend.forecast;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Service
public class ForecastServiceClient {

    private static final String EXPECTED_CODE_PATH = "weekly-xgboost-residual-v1";
    private static final java.util.Set<String> ALLOWED_CONFIDENCE_VALUES = java.util.Set.of("HIGH", "MEDIUM", "LOW");
    private static final java.util.Set<String> ALLOWED_REORDER_STATUS_VALUES = java.util.Set.of("GREEN", "AMBER", "RED");
    private static final java.util.Set<String> ALLOWED_MODEL_PATH_VALUES = java.util.Set.of(
            "xgboost_residual_interval",
            "fallback_recent_trend",
            "fallback_unsafe_xgboost_output",
            "prophet",
            "fallback_unsafe_prophet_output"
    );
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(35);
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ForecastServiceClient.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final String baseUrl;

    @Autowired
    public ForecastServiceClient(@Value("${pharmaforecast.forecast-service-url:}") String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl;
        LOGGER.info("Initializing ForecastServiceClient with baseUrl='{}'", this.baseUrl);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        requestFactory.setReadTimeout((int) READ_TIMEOUT.toMillis());
        this.restClient = RestClient.builder()
                .baseUrl(this.baseUrl)
                .requestFactory(requestFactory)
                .defaultHeaders(httpHeaders -> {
                    httpHeaders.add("X-Forecast-Code-Path", EXPECTED_CODE_PATH);
                })
                .build();
    }

    public ForecastServiceClient(RestClient restClient, String baseUrl) {
        this.restClient = restClient;
        this.baseUrl = baseUrl == null ? "" : baseUrl;
    }

    public ForecastResult generateForecast(ForecastRequest request) {
        if (baseUrl.isBlank()) {
            logCall(request.locationId(), request.din(), 0, "FORECAST_SERVICE_NOT_CONFIGURED");
            LOGGER.error("Forecast service URL is not configured (blank). Check FORECAST_SERVICE_URL or FORECAST_SERVICE_BASE_URL environment variable. Current baseUrl='{}'", baseUrl);
            throw new ForecastServiceUnavailableException("Forecast service is not configured. Please set FORECAST_SERVICE_URL environment variable.");
        }
        long start = System.nanoTime();
        try {
            ForecastResult result = restClient.post()
                    .uri("/forecast/drug")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange((clientRequest, clientResponse) -> {
                        String codePath = clientResponse.getHeaders().getFirst("X-Forecast-Code-Path");
                        if (!EXPECTED_CODE_PATH.equals(codePath)) {
                            throw new ForecastServiceUnavailableException(
                                    "Forecast service is not running the expected build. Expected X-Forecast-Code-Path=%s"
                                            .formatted(EXPECTED_CODE_PATH)
                            );
                        }
                        try {
                            ForecastResult forecastResult = OBJECT_MAPPER.readValue(clientResponse.getBody(), ForecastResult.class);
                            validateForecastResult(forecastResult);
                            return forecastResult;
                        } catch (IOException ex) {
                            throw new ForecastServiceUnavailableException("Forecast service response could not be parsed", ex);
                        }
                    });
            logCall(request.locationId(), request.din(), start, "OK");
            return result;
        } catch (ForecastServiceUnavailableException ex) {
            logCall(request.locationId(), request.din(), start, "FORECAST_SERVICE_UNAVAILABLE");
            LOGGER.error("Forecast service unavailable for locationId={}, din={}, error={}", request.locationId(), request.din(), ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            LOGGER.error("Forecast service call failed for locationId={}, din={}, error_type={}, error_message={}",
                    request.locationId(), request.din(), ex.getClass().getSimpleName(), ex.getMessage());
            logCall(request.locationId(), request.din(), start, "FORECAST_UNAVAILABLE");
            return unavailable(request.din(), request.locationId(), request.horizonDays());
        }
    }

    public InputStream generateBatchForecast(BatchForecastRequest request) {
        long start = System.nanoTime();
        try {
            InputStream body = restClient.post()
                    .uri("/forecast/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange((clientRequest, clientResponse) -> clientResponse.getBody());
            logCall(request.locationId(), request.dins() == null || request.dins().isEmpty() ? "" : request.dins().get(0), start, "OK");
            return body;
        } catch (Exception ex) {
            logCall(request.locationId(), request.dins() == null || request.dins().isEmpty() ? "" : request.dins().get(0), start, "FORECAST_UNAVAILABLE");
            return InputStream.nullInputStream();
        }
    }

    public NotificationCheckResult runNotificationCheck(String locationId) {
        long start = System.nanoTime();
        try {
            NotificationCheckResult result = restClient.post()
                    .uri("/forecast/notification-check")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new NotificationCheckRequest(locationId))
                    .retrieve()
                    .body(NotificationCheckResult.class);
            logCall(locationId, "", start, "OK");
            return result == null ? new NotificationCheckResult(java.util.List.of()) : result;
        } catch (Exception ex) {
            logCall(locationId, "", start, "FORECAST_UNAVAILABLE");
            return new NotificationCheckResult(java.util.List.of());
        }
    }

    private ForecastResult unavailable(String din, String locationId, Integer horizonDays) {
        return new ForecastResult(
                din,
                locationId,
                horizonDays,
                0,
                0,
                0,
                "LOW",
                0.0,
                0.0,
                "RED",
                "forecast_service_unavailable",
                0.0,
                Instant.now().toString(),
                0
        );
    }

    private void validateForecastResult(ForecastResult result) {
        if (result == null) {
            throw new ForecastServiceUnavailableException("Forecast service response was empty");
        }
        if (isBlank(result.din())) {
            throw missingField("din");
        }
        if (isBlank(result.locationId())) {
            throw missingField("location_id");
        }
        if (result.horizonDays() == null) {
            throw missingField("horizon_days");
        }
        if (result.predictedQuantity() == null) {
            throw missingField("predicted_quantity");
        }
        if (result.prophetLower() == null) {
            throw missingField("prophet_lower");
        }
        if (result.prophetUpper() == null) {
            throw missingField("prophet_upper");
        }
        if (isBlank(result.confidence())) {
            throw missingField("confidence");
        }
        if (!ALLOWED_CONFIDENCE_VALUES.contains(result.confidence())) {
            throw new ForecastServiceUnavailableException("Forecast service returned an invalid confidence value: " + result.confidence());
        }
        if (result.daysOfSupply() == null) {
            throw missingField("days_of_supply");
        }
        if (result.avgDailyDemand() == null) {
            throw missingField("avg_daily_demand");
        }
        if (isBlank(result.reorderStatus())) {
            throw missingField("reorder_status");
        }
        if (!ALLOWED_REORDER_STATUS_VALUES.contains(result.reorderStatus())) {
            throw new ForecastServiceUnavailableException("Forecast service returned an invalid reorder_status value: " + result.reorderStatus());
        }
        if (isBlank(result.modelPath())) {
            throw missingField("model_path");
        }
        if (!ALLOWED_MODEL_PATH_VALUES.contains(result.modelPath())) {
            throw new ForecastServiceUnavailableException("Forecast service returned an invalid model_path value: " + result.modelPath());
        }
        if (result.reorderPoint() == null) {
            throw missingField("reorder_point");
        }
        if (isBlank(result.generatedAt())) {
            throw missingField("generated_at");
        }
        if (result.dataPointsUsed() == null) {
            throw missingField("data_points_used");
        }
    }

    private ForecastServiceUnavailableException missingField(String field) {
        return new ForecastServiceUnavailableException("Forecast service response is missing required field: " + field);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void logCall(String locationId, String din, long startNanos, String status) {
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        LOGGER.info("forecast_service call locationId={} din={} duration_ms={} status={} service_url={}", locationId, din, durationMs, status, baseUrl);
    }
}
