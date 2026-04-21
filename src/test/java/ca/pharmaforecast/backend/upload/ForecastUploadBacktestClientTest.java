package ca.pharmaforecast.backend.upload;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.http.HttpMethod.POST;

class ForecastUploadBacktestClientTest {

    @Test
    void postsUploadedRowsToBacktestUploadEndpointAndReturnsSummary() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://forecast.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ForecastUploadBacktestClient client = new ForecastUploadBacktestClient(
                builder.build(),
                "http://forecast.test",
                true
        );
        UUID organizationId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID locationId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID uploadId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        server.expect(requestTo("http://forecast.test/backtest/upload"))
                .andExpect(method(POST))
                .andExpect(content().json("""
                        {
                          "organization_id": "11111111-1111-1111-1111-111111111111",
                          "location_id": "22222222-2222-2222-2222-222222222222",
                          "csv_upload_id": "33333333-3333-3333-3333-333333333333",
                          "model_version": "prophet_v1",
                          "debug_artifacts": false,
                          "rows": [
                            {
                              "dispensed_date": "2026-04-19",
                              "din": "00012345",
                              "quantity_dispensed": 7,
                              "cost_per_unit": 1.25
                            }
                          ]
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "status": "PASS",
                          "model_version": "prophet_v1",
                          "wape": 0.12,
                          "rows_evaluated": 1,
                          "generated_at": "2026-04-21T05:00:00+00:00",
                          "error_message": null,
                          "artifact_path": null
                        }
                        """, MediaType.APPLICATION_JSON));

        Map<String, Object> summary = client.runUploadBacktest(BacktestUploadRequest.prophetV1(
                organizationId,
                locationId,
                uploadId,
                List.of(new BacktestDemandRow("2026-04-19", "00012345", 7, new BigDecimal("1.25")))
        ));

        assertThat(summary).containsEntry("status", "PASS");
        assertThat(summary).containsEntry("wape", 0.12);
        server.verify();
    }

    @Test
    void missingForecastServiceUrlReturnsConfiguredErrorSummary() {
        ForecastUploadBacktestClient client = new ForecastUploadBacktestClient(
                RestClient.create(),
                "",
                true
        );

        Map<String, Object> summary = client.runUploadBacktest(BacktestUploadRequest.prophetV1(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(new BacktestDemandRow("2026-04-19", "00012345", 7, null))
        ));

        assertThat(summary).containsEntry("status", "ERROR");
        assertThat(summary).containsEntry("error_message", "forecast_service_not_configured");
    }

    @Test
    void disabledBacktestReturnsExplicitErrorSummaryInsteadOfNull() {
        ForecastUploadBacktestClient client = new ForecastUploadBacktestClient(
                RestClient.create(),
                "http://forecast.test",
                false
        );

        Map<String, Object> summary = client.runUploadBacktest(BacktestUploadRequest.prophetV1(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(new BacktestDemandRow("2026-04-19", "00012345", 7, null))
        ));

        assertThat(summary).containsEntry("status", "ERROR");
        assertThat(summary).containsEntry("error_message", "backtest_disabled");
    }

    @Test
    void timeoutReturnsBacktestTimeoutSummary() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://forecast.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ForecastUploadBacktestClient client = new ForecastUploadBacktestClient(
                builder.build(),
                "http://forecast.test",
                true
        );
        server.expect(requestTo("http://forecast.test/backtest/upload"))
                .andRespond(withException(new java.net.SocketTimeoutException("read timed out")));

        Map<String, Object> summary = client.runUploadBacktest(BacktestUploadRequest.prophetV1(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(new BacktestDemandRow("2026-04-19", "00012345", 7, null))
        ));

        assertThat(summary).containsEntry("status", "ERROR");
        assertThat(summary).containsEntry("error_message", "backtest_timeout");
    }
}
