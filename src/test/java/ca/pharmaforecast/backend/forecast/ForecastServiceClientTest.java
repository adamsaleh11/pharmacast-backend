package ca.pharmaforecast.backend.forecast;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ForecastServiceClientTest {

    @Test
    void generateForecastSendsPatchedRequestAndRequiresPatchedCodePathHeader() throws Exception {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://forecast.example");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ForecastServiceClient client = new ForecastServiceClient(builder.build(), "https://forecast.example");

        ForecastRequest request = new ForecastRequest(
                "loc-123",
                "00012345",
                14,
                42,
                5,
                1.5,
                3,
                7,
                List.of(new WeeklyQuantityDto("2026-04-13", 70))
        );
        String expectedBody = new ObjectMapper().writeValueAsString(request);

        server.expect(requestTo("https://forecast.example/forecast/drug"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(expectedBody))
                .andRespond(withSuccess("""
                        {
                          "din":"00012345",
                          "location_id":"loc-123",
                          "horizon_days":14,
                          "predicted_quantity":280,
                          "prophet_lower":240,
                          "prophet_upper":320,
                          "confidence":"HIGH",
                          "days_of_supply":10.0,
                          "avg_daily_demand":20.0,
                          "reorder_status":"GREEN",
                          "reorder_point":60.0,
                          "generated_at":"2026-04-20T12:00:00Z",
                          "data_points_used":14
                        }
                        """, MediaType.APPLICATION_JSON)
                        .header("X-Forecast-Code-Path", "weekly-normalized-samples-v2"));

        ForecastResult result = client.generateForecast(request);

        assertThat(result.predictedQuantity()).isEqualTo(280);
        assertThat(result.reorderStatus()).isEqualTo("GREEN");
        server.verify();
    }

    @Test
    void generateForecastFailsClosedWhenPatchedCodePathHeaderIsMissing() throws Exception {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://forecast.example");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ForecastServiceClient client = new ForecastServiceClient(builder.build(), "https://forecast.example");

        ForecastRequest request = new ForecastRequest(
                "loc-123",
                "00012345",
                14,
                42,
                5,
                1.5,
                3,
                7,
                List.of()
        );

        server.expect(requestTo("https://forecast.example/forecast/drug"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "din":"00012345",
                          "location_id":"loc-123",
                          "horizon_days":14,
                          "predicted_quantity":280,
                          "prophet_lower":240,
                          "prophet_upper":320,
                          "confidence":"HIGH",
                          "days_of_supply":10.0,
                          "avg_daily_demand":20.0,
                          "reorder_status":"GREEN",
                          "reorder_point":60.0,
                          "generated_at":"2026-04-20T12:00:00Z",
                          "data_points_used":14
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.generateForecast(request))
                .isInstanceOf(ForecastServiceUnavailableException.class)
                .hasMessageContaining("patched build");
        server.verify();
    }
}
