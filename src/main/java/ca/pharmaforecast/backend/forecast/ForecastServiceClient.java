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

    private static final String PATCHED_CODE_PATH = "weekly-normalized-samples-v2";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(35);
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ForecastServiceClient.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final String baseUrl;

    @Autowired
    public ForecastServiceClient(@Value("${pharmaforecast.forecast-service-url:}") String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        requestFactory.setReadTimeout((int) READ_TIMEOUT.toMillis());
        this.restClient = RestClient.builder()
                .baseUrl(this.baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public ForecastServiceClient(RestClient restClient, String baseUrl) {
        this.restClient = restClient;
        this.baseUrl = baseUrl == null ? "" : baseUrl;
    }

    public ForecastResult generateForecast(ForecastRequest request) {
        if (baseUrl.isBlank()) {
            logCall(request.locationId(), request.din(), 0, "FORECAST_SERVICE_NOT_CONFIGURED");
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
                        if (!PATCHED_CODE_PATH.equals(codePath)) {
                            throw new ForecastServiceUnavailableException(
                                    "Forecast service is not running the patched build. Expected X-Forecast-Code-Path=%s"
                                            .formatted(PATCHED_CODE_PATH)
                            );
                        }
                        try {
                            return OBJECT_MAPPER.readValue(clientResponse.getBody(), ForecastResult.class);
                        } catch (IOException ex) {
                            throw new ForecastServiceUnavailableException("Forecast service response could not be parsed", ex);
                        }
                    });
            logCall(request.locationId(), request.din(), start, "OK");
            return result;
        } catch (ForecastServiceUnavailableException ex) {
            logCall(request.locationId(), request.din(), start, "FORECAST_SERVICE_UNAVAILABLE");
            throw ex;
        } catch (Exception ex) {
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
                0.0,
                Instant.now().toString(),
                0
        );
    }

    private void logCall(String locationId, String din, long startNanos, String status) {
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        LOGGER.info("forecast_service call locationId={} din={} duration_ms={} status={}", locationId, din, durationMs, status);
    }
}
