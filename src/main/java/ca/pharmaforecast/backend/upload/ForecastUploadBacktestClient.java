package ca.pharmaforecast.backend.upload;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ForecastUploadBacktestClient implements UploadBacktestPort {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ForecastUploadBacktestClient.class);

    private final RestClient restClient;
    private final String baseUrl;
    private final boolean enabled;

    @Autowired
    public ForecastUploadBacktestClient(
            @Value("${pharmaforecast.forecast-service-url:}") String baseUrl,
            @Value("${pharmaforecast.backtest-on-upload-enabled:true}") boolean enabled,
            @Value("${pharmaforecast.backtest-http-timeout-seconds:60}") int timeoutSeconds
    ) {
        this.baseUrl = baseUrl == null ? "" : baseUrl;
        this.enabled = enabled;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofSeconds(timeoutSeconds).toMillis());
        this.restClient = RestClient.builder()
                .baseUrl(this.baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    ForecastUploadBacktestClient(RestClient restClient, String baseUrl, boolean enabled) {
        this.restClient = restClient;
        this.baseUrl = baseUrl == null ? "" : baseUrl;
        this.enabled = enabled;
    }

    @Override
    public Map<String, Object> runUploadBacktest(BacktestUploadRequest request) {
        if (!enabled) {
            return errorSummary(request.modelVersion(), "backtest_disabled");
        }
        if (baseUrl.isBlank()) {
            return errorSummary(request.modelVersion(), "forecast_service_not_configured");
        }
        try {
            Map<String, Object> summary = restClient.post()
                    .uri("/backtest/upload")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return summary == null ? errorSummary(request.modelVersion(), "empty_backtest_response") : summary;
        } catch (ResourceAccessException ex) {
            String errorMessage = isTimeout(ex) ? "backtest_timeout" : "backtest_unavailable";
            LOGGER.warn("upload backtest failed for upload {} status={} error={}", request.csvUploadId(), errorMessage, ex.toString());
            return errorSummary(request.modelVersion(), errorMessage);
        } catch (Exception ex) {
            LOGGER.warn("upload backtest failed for upload {} status=backtest_unavailable error={}", request.csvUploadId(), ex.toString());
            return errorSummary(request.modelVersion(), "backtest_unavailable");
        }
    }

    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Map<String, Object> errorSummary(String modelVersion, String errorMessage) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("status", "ERROR");
        summary.put("model_version", modelVersion);
        summary.put("mae", null);
        summary.put("wape", null);
        summary.put("interval_coverage", null);
        summary.put("anomaly_count", null);
        summary.put("beats_last_7_day_avg", null);
        summary.put("beats_last_14_day_avg", null);
        summary.put("baseline_last_7_day_avg_mae", null);
        summary.put("baseline_last_14_day_avg_mae", null);
        summary.put("rows_evaluated", null);
        summary.put("raw_rows_received", null);
        summary.put("usable_rows", null);
        summary.put("min_required_rows", null);
        summary.put("date_range", null);
        summary.put("ready_for_forecast", false);
        summary.put("model_path_counts", Map.of());
        summary.put("din_count", null);
        summary.put("generated_at", OffsetDateTime.now(ZoneOffset.UTC).toString());
        summary.put("error_message", errorMessage);
        summary.put("artifact_path", null);
        return summary;
    }
}
