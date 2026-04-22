package ca.pharmaforecast.backend.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class LlmServiceClientTest {

    @Test
    void getExplanationPostsExplainPayloadToLlmService() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://llm.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LlmServiceClient client = new LlmServiceClient(builder.build(), "http://llm.test");

        ExplainPayload payload = new ExplainPayload(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "12345678",
                "Amoxicillin",
                "500 mg",
                "Antibiotic",
                15,
                new BigDecimal("4.5"),
                new BigDecimal("3.2"),
                14,
                45,
                new BigDecimal("40"),
                new BigDecimal("51"),
                "HIGH",
                "RED",
                new BigDecimal("12"),
                2,
                28,
                List.of(4, 5, 6, 7, 8, 9, 10, 11),
                600
        );

        server.expect(requestTo("http://llm.test/llm/explain"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "location_id": "11111111-1111-1111-1111-111111111111",
                          "din": "12345678",
                          "drug_name": "Amoxicillin",
                          "strength": "500 mg",
                          "therapeutic_class": "Antibiotic",
                          "quantity_on_hand": 15,
                          "days_of_supply": 4.5,
                          "avg_daily_demand": 3.2,
                          "horizon_days": 14,
                          "predicted_quantity": 45,
                          "prophet_lower": 40,
                          "prophet_upper": 51,
                          "confidence": "HIGH",
                          "reorder_status": "RED",
                          "reorder_point": 12,
                          "lead_time_days": 2,
                          "data_points_used": 28,
                          "weekly_quantities": [4, 5, 6, 7, 8, 9, 10, 11],
                          "max_tokens": 600
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "explanation": "Reorder now.",
                          "generated_at": "2026-04-21T00:00:00Z"
                        }
                        """, MediaType.APPLICATION_JSON));

        ExplanationResult result = client.getExplanation(payload);

        assertThat(result.explanation()).isEqualTo("Reorder now.");
        assertThat(result.generatedAt()).isEqualTo(Instant.parse("2026-04-21T00:00:00Z"));
        assertThat(result.error()).isNull();
        server.verify();
    }

    @Test
    void generatePurchaseOrderTextPostsPurchaseOrderPayloadToLlmService() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://llm.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LlmServiceClient client = new LlmServiceClient(builder.build(), "http://llm.test");

        PurchaseOrderPayload payload = new PurchaseOrderPayload(
                "Downtown Pharmacy",
                "123 Bank St, Ottawa, ON",
                "2026-04-21",
                14,
                List.of(new PurchaseOrderDrugPayload(
                        "Amoxicillin",
                        "500 mg",
                        "12345678",
                        8,
                        40,
                        new BigDecimal("2.5"),
                        "RED",
                        new BigDecimal("3.1"),
                        2
                )),
                1500
        );

        server.expect(requestTo("http://llm.test/llm/purchase-order"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "pharmacy_name": "Downtown Pharmacy",
                          "location_address": "123 Bank St, Ottawa, ON",
                          "today": "2026-04-21",
                          "horizon_days": 14,
                          "drugs": [
                            {
                              "drug_name": "Amoxicillin",
                              "strength": "500 mg",
                              "din": "12345678",
                              "current_stock": 8,
                              "predicted_quantity": 40,
                              "days_of_supply": 2.5,
                              "reorder_status": "RED",
                              "avg_daily_demand": 3.1,
                              "lead_time_days": 2
                            }
                          ],
                          "max_tokens": 1500
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "order_text": "Order Amoxicillin now.",
                          "generated_at": "2026-04-21T00:00:00Z"
                        }
                        """, MediaType.APPLICATION_JSON));

        PurchaseOrderTextResult result = client.generatePurchaseOrderText(payload);

        assertThat(result.orderText()).isEqualTo("Order Amoxicillin now.");
        assertThat(result.generatedAt()).isEqualTo(Instant.parse("2026-04-21T00:00:00Z"));
        assertThat(result.error()).isNull();
        server.verify();
    }

    @Test
    void streamChatReturnsRawSseStream() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://llm.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LlmServiceClient client = new LlmServiceClient(builder.build(), "http://llm.test");

        ChatPayload payload = new ChatPayload(
                "You are helpful.",
                List.of(Map.of("role", "user", "content", "Say hello"))
        );

        server.expect(requestTo("http://llm.test/llm/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "system_prompt": "You are helpful.",
                          "messages": [
                            {
                              "role": "user",
                              "content": "Say hello"
                            }
                          ]
                        }
                        """))
                .andRespond(withSuccess("""
                        data: {"token":"Hel"}

                        data: {"token":"lo"}

                        data: {"done":true,"total_tokens":1}
                        """, MediaType.valueOf("text/event-stream")));

        try (InputStream stream = client.streamChat(payload)) {
            String body = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertThat(body).contains("token");
            assertThat(body).contains("done");
        }
        server.verify();
    }

    @Test
    void getExplanationReturnsUnavailableResultWhenLlmRespondsWithServerError() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://llm.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        LlmServiceClient client = new LlmServiceClient(builder.build(), "http://llm.test");

        ExplainPayload payload = new ExplainPayload(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "12345678",
                "Amoxicillin",
                "500 mg",
                "Antibiotic",
                15,
                new BigDecimal("4.5"),
                new BigDecimal("3.2"),
                14,
                45,
                new BigDecimal("40"),
                new BigDecimal("51"),
                "HIGH",
                "RED",
                new BigDecimal("12"),
                2,
                28,
                List.of(4, 5, 6, 7, 8, 9, 10, 11),
                600
        );

        server.expect(requestTo("http://llm.test/llm/explain"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));

        ExplanationResult result = client.getExplanation(payload);

        assertThat(result.error()).isEqualTo("LLM_UNAVAILABLE");
        assertThat(result.explanation()).isNull();
        assertThat(result.generatedAt()).isNull();
        server.verify();
    }
}
