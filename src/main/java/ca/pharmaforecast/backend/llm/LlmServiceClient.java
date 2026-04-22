package ca.pharmaforecast.backend.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Service
public class LlmServiceClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(LlmServiceClient.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String LLM_UNAVAILABLE_ERROR = "{\"error\":\"LLM_UNAVAILABLE\",\"message\":\"Try again in a moment\"}";

    private final RestClient restClient;
    private final String baseUrl;

    @Autowired
    public LlmServiceClient(@Value("${LLM_SERVICE_URL:}") String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) Duration.ofSeconds(30).toMillis());
        requestFactory.setReadTimeout((int) Duration.ofSeconds(30).toMillis());
        this.restClient = RestClient.builder()
                .baseUrl(this.baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    LlmServiceClient(RestClient restClient, String baseUrl) {
        this.restClient = restClient;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
    }

    public ExplanationResult getExplanation(ExplainPayload payload) {
        return executeJson("/llm/explain", payload, node -> new ExplanationResult(
                text(node, "explanation"),
                parseInstant(text(node, "generated_at")),
                null
        ));
    }

    public InputStream streamChat(ChatPayload payload) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        streamChat(payload, stream -> stream.transferTo(output));
        return new ByteArrayInputStream(output.toByteArray());
    }

    public void streamChat(ChatPayload payload, StreamHandler streamHandler) {
        executeStream("/llm/chat", payload, streamHandler);
    }

    public PurchaseOrderTextResult generatePurchaseOrderText(PurchaseOrderPayload payload) {
        return executeJson("/llm/purchase-order", payload, node -> new PurchaseOrderTextResult(
                text(node, "order_text"),
                parseInstant(text(node, "generated_at")),
                null
        ));
    }

    private <T> T executeJson(String endpoint, Object payload, ResponseMapper<T> mapper) {
        long start = System.nanoTime();
        if (!StringUtils.hasText(baseUrl)) {
            logCall(endpoint, start, false);
            return unavailableResult(endpoint);
        }
        try {
            T result = restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            return unavailableResult(endpoint);
                        }
                        try (InputStream body = response.getBody()) {
                            JsonNode node = OBJECT_MAPPER.readTree(body);
                            if (node == null) {
                                return unavailableResult(endpoint);
                            }
                            return mapper.map(node);
                        }
                    });
            logCall(endpoint, start, !isUnavailable(result));
            return result == null ? unavailableResult(endpoint) : result;
        } catch (ResourceAccessException ex) {
            logCall(endpoint, start, false);
            return unavailableResult(endpoint);
        } catch (Exception ex) {
            logCall(endpoint, start, false);
            return unavailableResult(endpoint);
        }
    }

    private void executeStream(String endpoint, Object payload, StreamHandler streamHandler) {
        long start = System.nanoTime();
        if (!StringUtils.hasText(baseUrl)) {
            logCall(endpoint, start, false);
            streamError(streamHandler);
            return;
        }
        try {
            Boolean success = restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .body(payload)
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            streamError(streamHandler);
                            return false;
                        }
                        try (InputStream body = response.getBody()) {
                            if (body == null) {
                                streamError(streamHandler);
                                return false;
                            }
                            handleStream(streamHandler, body);
                            return true;
                        }
                    });
            logCall(endpoint, start, Boolean.TRUE.equals(success));
        } catch (ResourceAccessException ex) {
            logCall(endpoint, start, false);
            streamError(streamHandler);
        } catch (StreamHandlingException ex) {
            logCall(endpoint, start, false);
            throw ex;
        } catch (Exception ex) {
            logCall(endpoint, start, false);
            streamError(streamHandler);
        }
    }

    private void handleStream(StreamHandler streamHandler, InputStream stream) {
        try {
            streamHandler.handle(stream);
        } catch (IOException ex) {
            throw new StreamHandlingException(ex);
        } catch (RuntimeException ex) {
            throw new StreamHandlingException(ex);
        }
    }

    private void streamError(StreamHandler streamHandler) {
        try (InputStream stream = errorStream()) {
            streamHandler.handle(stream);
        } catch (IOException ignored) {
            // The chat service owns client-visible SSE failure handling.
        }
    }

    private boolean isUnavailable(Object result) {
        if (result instanceof ExplanationResult explanationResult) {
            return explanationResult.error() != null;
        }
        if (result instanceof PurchaseOrderTextResult purchaseOrderTextResult) {
            return purchaseOrderTextResult.error() != null;
        }
        return false;
    }

    private <T> T unavailableResult(String endpoint) {
        if ("/llm/explain".equals(endpoint)) {
            @SuppressWarnings("unchecked")
            T result = (T) new ExplanationResult(null, null, "LLM_UNAVAILABLE");
            return result;
        }
        if ("/llm/purchase-order".equals(endpoint)) {
            @SuppressWarnings("unchecked")
            T result = (T) new PurchaseOrderTextResult(null, null, "LLM_UNAVAILABLE");
            return result;
        }
        throw new IllegalArgumentException("Unsupported endpoint " + endpoint);
    }

    private InputStream errorStream() {
        return new ByteArrayInputStream(("data: " + LLM_UNAVAILABLE_ERROR + "\n\n").getBytes(StandardCharsets.UTF_8));
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Instant.parse(value);
    }

    private void logCall(String endpoint, long startNanos, boolean success) {
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
        LOGGER.info("llm_service call endpoint={} duration_ms={} success={} base_url={}", endpoint, durationMs, success, baseUrl);
    }

    @FunctionalInterface
    private interface ResponseMapper<T> {
        T map(JsonNode node) throws IOException;
    }

    @FunctionalInterface
    public interface StreamHandler {
        void handle(InputStream stream) throws IOException;
    }

    private static class StreamHandlingException extends RuntimeException {
        StreamHandlingException(Throwable cause) {
            super(cause);
        }
    }
}
