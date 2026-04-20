package ca.pharmaforecast.backend.drug;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Semaphore;

@Service
public class HealthCanadaApiClient {

    private static final String BASE_URL = "https://health-products.canada.ca/api/drug";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Semaphore semaphore = new Semaphore(5);

    @Autowired
    public HealthCanadaApiClient(RestClient.Builder restClientBuilder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = restClientBuilder
                .baseUrl(BASE_URL)
                .requestFactory(requestFactory)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public HealthCanadaApiClient(RestClient restClient) {
        this.restClient = restClient;
        this.objectMapper = new ObjectMapper();
    }

    public Optional<HealthCanadaDrugProduct> fetchDrugProduct(String din) {
        return getFirst("/drugproduct/", "din", din, HealthCanadaDrugProduct.class);
    }

    public Optional<List<HealthCanadaActiveIngredient>> fetchActiveIngredient(String drugProductId) {
        return getList("/activeingredient/", "id", drugProductId, new TypeReference<>() {
        });
    }

    public Optional<List<HealthCanadaForm>> fetchForm(String drugProductId) {
        return getList("/form/", "id", drugProductId, new TypeReference<>() {
        });
    }

    public Optional<List<HealthCanadaSchedule>> fetchSchedule(String drugProductId) {
        return getList("/schedule/", "id", drugProductId, new TypeReference<>() {
        });
    }

    public Optional<List<HealthCanadaProductStatus>> fetchStatus(String drugProductId) {
        return getList("/status/", "id", drugProductId, new TypeReference<>() {
        });
    }

    public Optional<List<HealthCanadaTherapeuticClass>> fetchTherapeuticClass(String drugProductId) {
        return getList("/therapeuticclass/", "id", drugProductId, new TypeReference<>() {
        });
    }

    private <T> Optional<T> getFirst(String path, String parameter, String value, Class<T> type) {
        return getNode(path, parameter, value)
                .flatMap(node -> {
                    JsonNode first = firstNode(node);
                    if (first == null) {
                        return Optional.empty();
                    }
                    return Optional.of(objectMapper.convertValue(first, type));
                });
    }

    private <T> Optional<List<T>> getList(String path, String parameter, String value, TypeReference<List<T>> type) {
        return getNode(path, parameter, value)
                .flatMap(node -> {
                    if (node.isArray() && node.isEmpty()) {
                        return Optional.empty();
                    }
                    JsonNode listNode = node.isArray() ? node : objectMapper.createArrayNode().add(node);
                    return Optional.of(objectMapper.convertValue(listNode, type));
                });
    }

    private Optional<JsonNode> getNode(String path, String parameter, String value) {
        boolean acquired = false;
        try {
            semaphore.acquire();
            acquired = true;
            String body = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(path)
                            .queryParam("lang", "en")
                            .queryParam("type", "json")
                            .queryParam(parameter, value)
                            .build())
                    .retrieve()
                    .body(String.class);
            sleepBetweenBatches();
            if (body == null || body.isBlank()) {
                return Optional.empty();
            }
            JsonNode node = objectMapper.readTree(body);
            if (node.isArray() && node.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(node);
        } catch (HttpClientErrorException.NotFound ex) {
            return Optional.empty();
        } catch (JsonProcessingException ex) {
            throw new HealthCanadaApiException("Health Canada response could not be parsed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new HealthCanadaApiException("Interrupted while waiting for Health Canada rate limiter", ex);
        } finally {
            if (acquired) {
                semaphore.release();
            }
        }
    }

    private JsonNode firstNode(JsonNode node) {
        if (node.isArray()) {
            return node.isEmpty() ? null : node.get(0);
        }
        return node;
    }

    private void sleepBetweenBatches() throws InterruptedException {
        Thread.sleep(100);
    }
}
