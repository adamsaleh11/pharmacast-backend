package ca.pharmaforecast.backend.forecast;

import ca.pharmaforecast.backend.dispensing.DispensingRecord;
import ca.pharmaforecast.backend.dispensing.DispensingRecordRepository;
import ca.pharmaforecast.backend.currentstock.CurrentStockRepository;
import ca.pharmaforecast.backend.currentstock.CurrentStock;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.Optional;

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
}
