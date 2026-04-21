package ca.pharmaforecast.backend.forecast;

import ca.pharmaforecast.backend.dispensing.DispensingRecord;
import ca.pharmaforecast.backend.dispensing.DispensingRecordRepository;
import ca.pharmaforecast.backend.currentstock.CurrentStockRepository;
import ca.pharmaforecast.backend.currentstock.CurrentStock;
import ca.pharmaforecast.backend.forecast.InvalidForecastResultException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ForecastServiceTest {

    @Test
    void generateForecastPersistsUpstreamResultForOwnedLocation() throws Exception {
        UUID locationId = UUID.randomUUID();
        String din = "00012345";

        ForecastRequestAssembler forecastRequestAssembler = mock(ForecastRequestAssembler.class);
        ForecastRepository forecastRepository = mock(ForecastRepository.class);
        ForecastServiceClient forecastServiceClient = mock(ForecastServiceClient.class);
        CurrentStockRepository currentStockRepository = mock(CurrentStockRepository.class);

        when(forecastRequestAssembler.build(locationId, din, 7)).thenReturn(new ForecastRequest(
                locationId.toString(),
                din,
                7,
                30,
                2,
                1.0,
                3,
                7,
                null
        ));
        CurrentStock currentStock = new CurrentStock();
        currentStock.setLocationId(locationId);
        currentStock.setDin(din);
        currentStock.setQuantity(30);
        when(currentStockRepository.findByLocationIdAndDin(locationId, din)).thenReturn(Optional.of(currentStock));
        when(forecastServiceClient.generateForecast(any())).thenReturn(new ForecastResult(
                din,
                locationId.toString(),
                7,
                12,
                10,
                15,
                "HIGH",
                4.5,
                2.0,
                "RED",
                6.0,
                "2026-04-20T12:00:00Z",
                21
        ));

        ForecastService service = new ForecastService(
                forecastRequestAssembler,
                forecastServiceClient,
                forecastRepository,
                currentStockRepository
        );

        ForecastResult result = service.generateForecast(locationId, din, 7);

        assertThat(result.din()).isEqualTo(din);
        verify(forecastRepository).save(any(Forecast.class));
    }

    @Test
    void persistForecastRejectsInvalidProphetInterval() throws Exception {
        UUID locationId = UUID.randomUUID();
        String din = "00012345";

        ForecastRequestAssembler forecastRequestAssembler = mock(ForecastRequestAssembler.class);
        ForecastRepository forecastRepository = mock(ForecastRepository.class);
        ForecastServiceClient forecastServiceClient = mock(ForecastServiceClient.class);
        CurrentStockRepository currentStockRepository = mock(CurrentStockRepository.class);

        ForecastService service = new ForecastService(
                forecastRequestAssembler,
                forecastServiceClient,
                forecastRepository,
                currentStockRepository
        );

        assertThat(org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> service.persistForecast(locationId, new ForecastResult(
                        din,
                        locationId.toString(),
                        7,
                        12,
                        0,
                        -345,
                        "HIGH",
                        4.5,
                        2.0,
                        "RED",
                        6.0,
                        "2026-04-20T12:00:00Z",
                        21
                )),
                InvalidForecastResultException.class
        )).hasMessageContaining("invalid Prophet interval");
        verify(forecastRepository, org.mockito.Mockito.never()).save(any(Forecast.class));
    }

    @Test
    void streamBatchForecastEmitsErrorForInvalidForecastResultInsteadOfCrashing() throws Exception {
        UUID locationId = UUID.randomUUID();
        String din = "00012345";

        ForecastRequestAssembler forecastRequestAssembler = mock(ForecastRequestAssembler.class);
        ForecastRepository forecastRepository = mock(ForecastRepository.class);
        ForecastServiceClient forecastServiceClient = mock(ForecastServiceClient.class);
        CurrentStockRepository currentStockRepository = mock(CurrentStockRepository.class);

        when(currentStockRepository.getStockMapForLocation(locationId)).thenReturn(Map.of(din, 30));
        when(forecastServiceClient.generateBatchForecast(any())).thenReturn(
                new ByteArrayInputStream("""
                        data: {"din":"%s","forecast":{"din":"%s","location_id":"%s","horizon_days":7,"predicted_quantity":12,"prophet_lower":10,"prophet_upper":5,"confidence":"HIGH","days_of_supply":4.5,"avg_daily_demand":2.0,"reorder_status":"RED","reorder_point":6.0,"generated_at":"2026-04-20T12:00:00Z","data_points_used":21}}
                        data: {"status":"done"}
                        """.formatted(din, din, locationId).getBytes(StandardCharsets.UTF_8))
        );

        ForecastService service = new ForecastService(
                forecastRequestAssembler,
                forecastServiceClient,
                forecastRepository,
                currentStockRepository
        );

        List<ForecastBatchEvent> events = new ArrayList<>();
        service.streamBatchForecast(locationId, new BatchForecastRequest(locationId.toString(), List.of(din), 7, Map.of()), events::add);

        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(ForecastBatchEvent.Error.class);
        assertThat(((ForecastBatchEvent.Error) events.get(0)).error()).isEqualTo("INVALID_FORECAST_RESULT");
        assertThat(events.get(1)).isInstanceOf(ForecastBatchEvent.Done.class);
        assertThat(((ForecastBatchEvent.Done) events.get(1)).failed()).isEqualTo(1);
        verify(forecastRepository, org.mockito.Mockito.never()).save(any(Forecast.class));
    }

    @Test
    void streamBatchForecastAcceptsPatchedBatchCompleteEnvelope() throws Exception {
        UUID locationId = UUID.randomUUID();
        String din = "00012345";

        ForecastRequestAssembler forecastRequestAssembler = mock(ForecastRequestAssembler.class);
        ForecastRepository forecastRepository = mock(ForecastRepository.class);
        ForecastServiceClient forecastServiceClient = mock(ForecastServiceClient.class);
        CurrentStockRepository currentStockRepository = mock(CurrentStockRepository.class);

        when(currentStockRepository.getStockMapForLocation(locationId)).thenReturn(Map.of(din, 30));
        when(forecastServiceClient.generateBatchForecast(any())).thenReturn(
                new ByteArrayInputStream("""
                        data: {"din":"%s","status":"complete","result":{"din":"%s","location_id":"%s","horizon_days":7,"predicted_quantity":12,"prophet_lower":10,"prophet_upper":15,"confidence":"HIGH","days_of_supply":4.5,"avg_daily_demand":2.0,"reorder_status":"RED","reorder_point":6.0,"generated_at":"2026-04-20T12:00:00Z","data_points_used":21}}
                        data: {"done":true,"total":1,"succeeded":1,"failed":0,"skipped_no_stock":0,"skipped_dins":[]}
                        """.formatted(din, din, locationId).getBytes(StandardCharsets.UTF_8))
        );

        ForecastService service = new ForecastService(
                forecastRequestAssembler,
                forecastServiceClient,
                forecastRepository,
                currentStockRepository
        );

        List<ForecastBatchEvent> events = new ArrayList<>();
        service.streamBatchForecast(locationId, new BatchForecastRequest(locationId.toString(), List.of(din), 7, Map.of()), events::add);

        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(ForecastBatchEvent.Result.class);
        assertThat(((ForecastBatchEvent.Result) events.get(0)).forecast().predictedQuantity()).isEqualTo(12);
        assertThat(events.get(1)).isInstanceOf(ForecastBatchEvent.Done.class);
        assertThat(((ForecastBatchEvent.Done) events.get(1)).done()).isTrue();
        assertThat(((ForecastBatchEvent.Done) events.get(1)).succeeded()).isEqualTo(1);
        verify(forecastRepository).save(any(Forecast.class));
    }
}
