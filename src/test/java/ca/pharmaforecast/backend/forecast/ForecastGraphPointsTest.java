package ca.pharmaforecast.backend.forecast;

import ca.pharmaforecast.backend.currentstock.CurrentStockRepository;
import ca.pharmaforecast.backend.drug.Drug;
import ca.pharmaforecast.backend.drug.DrugRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ForecastGraphPointsTest {

    private final ForecastRepository forecastRepository = mock(ForecastRepository.class);
    private final DrugRepository drugRepository = mock(DrugRepository.class);
    private final DrugThresholdRepository drugThresholdRepository = mock(DrugThresholdRepository.class);
    private final CurrentStockRepository currentStockRepository = mock(CurrentStockRepository.class);

    private final ForecastReadService service = new ForecastReadService(
            forecastRepository,
            drugRepository,
            drugThresholdRepository,
            currentStockRepository
    );

    @Test
    void graphPointsArePopulatedFor7DayForecast() {
        // Arrange
        UUID locationId = UUID.randomUUID();
        String din = "00012345";
        Instant now = Instant.now();

        Forecast forecast = createForecast(din, locationId, 7, 140, now);
        when(forecastRepository.findByLocationIdOrderByGeneratedAtDesc(locationId))
                .thenReturn(List.of(forecast));

        Drug drug = new Drug();
        when(drugRepository.findByDinIn(any())).thenReturn(List.of(drug));

        when(drugThresholdRepository.findByLocationIdAndDin(locationId, din))
                .thenReturn(Optional.empty());

        when(currentStockRepository.findByLocationIdAndDin(locationId, din))
                .thenReturn(Optional.empty());

        // Act
        List<ForecastSummaryDto> results = service.getLatestForecasts(locationId, ForecastQueryParams.defaults());

        // Assert
        assertThat(results).hasSize(1);
        ForecastSummaryDto dto = results.get(0);
        assertThat(dto.graphPoints()).isNotNull();
        assertThat(dto.graphPoints()).hasSize(7);
        assertThat(dto.graphPoints()).allMatch(point -> point > 0);
    }

    @Test
    void graphPointsSumToApproximatePredictedQuantity() {
        // Arrange
        UUID locationId = UUID.randomUUID();
        String din = "00012345";
        Integer predictedQuantity = 140;
        Instant now = Instant.now();

        Forecast forecast = createForecast(din, locationId, 7, predictedQuantity, now);
        when(forecastRepository.findByLocationIdOrderByGeneratedAtDesc(locationId))
                .thenReturn(List.of(forecast));

        Drug drug = new Drug();
        when(drugRepository.findByDinIn(any())).thenReturn(List.of(drug));

        when(drugThresholdRepository.findByLocationIdAndDin(locationId, din))
                .thenReturn(Optional.empty());

        when(currentStockRepository.findByLocationIdAndDin(locationId, din))
                .thenReturn(Optional.empty());

        // Act
        List<ForecastSummaryDto> results = service.getLatestForecasts(locationId, ForecastQueryParams.defaults());

        // Assert
        ForecastSummaryDto dto = results.get(0);
        int sum = dto.graphPoints().stream().mapToInt(Integer::intValue).sum();
        assertThat(sum).isEqualTo(predictedQuantity);
    }

    @Test
    void graphPointsFor14DayForecastHas14Points() {
        // Arrange
        UUID locationId = UUID.randomUUID();
        String din = "00012345";
        Instant now = Instant.now();

        Forecast forecast = createForecast(din, locationId, 14, 280, now);
        when(forecastRepository.findByLocationIdOrderByGeneratedAtDesc(locationId))
                .thenReturn(List.of(forecast));

        Drug drug = new Drug();
        when(drugRepository.findByDinIn(any())).thenReturn(List.of(drug));

        when(drugThresholdRepository.findByLocationIdAndDin(locationId, din))
                .thenReturn(Optional.empty());

        when(currentStockRepository.findByLocationIdAndDin(locationId, din))
                .thenReturn(Optional.empty());

        // Act
        List<ForecastSummaryDto> results = service.getLatestForecasts(locationId, ForecastQueryParams.defaults());

        // Assert
        assertThat(results).hasSize(1);
        ForecastSummaryDto dto = results.get(0);
        assertThat(dto.graphPoints()).hasSize(14);
    }

    @Test
    void graphPointsFor30DayForecastHas30Points() {
        // Arrange
        UUID locationId = UUID.randomUUID();
        String din = "00012345";
        Instant now = Instant.now();

        Forecast forecast = createForecast(din, locationId, 30, 600, now);
        when(forecastRepository.findByLocationIdOrderByGeneratedAtDesc(locationId))
                .thenReturn(List.of(forecast));

        Drug drug = new Drug();
        when(drugRepository.findByDinIn(any())).thenReturn(List.of(drug));

        when(drugThresholdRepository.findByLocationIdAndDin(locationId, din))
                .thenReturn(Optional.empty());

        when(currentStockRepository.findByLocationIdAndDin(locationId, din))
                .thenReturn(Optional.empty());

        // Act
        List<ForecastSummaryDto> results = service.getLatestForecasts(locationId, ForecastQueryParams.defaults());

        // Assert
        assertThat(results).hasSize(1);
        ForecastSummaryDto dto = results.get(0);
        assertThat(dto.graphPoints()).hasSize(30);
    }

    @Test
    void graphPointsAreNullWhenNoForecasts() {
        // Arrange
        UUID locationId = UUID.randomUUID();
        when(forecastRepository.findByLocationIdOrderByGeneratedAtDesc(locationId))
                .thenReturn(List.of());

        when(drugRepository.findByDinIn(any())).thenReturn(List.of());

        // Act
        List<ForecastSummaryDto> results = service.getLatestForecasts(locationId, ForecastQueryParams.defaults());

        // Assert
        assertThat(results).isEmpty();
    }

    @Test
    void graphPointsHandlesRemainder() {
        // Arrange - 141 units across 7 days (should have remainder distribution)
        UUID locationId = UUID.randomUUID();
        String din = "00012345";
        Integer predictedQuantity = 141;
        Instant now = Instant.now();

        Forecast forecast = createForecast(din, locationId, 7, predictedQuantity, now);
        when(forecastRepository.findByLocationIdOrderByGeneratedAtDesc(locationId))
                .thenReturn(List.of(forecast));

        Drug drug = new Drug();
        when(drugRepository.findByDinIn(any())).thenReturn(List.of(drug));

        when(drugThresholdRepository.findByLocationIdAndDin(locationId, din))
                .thenReturn(Optional.empty());

        when(currentStockRepository.findByLocationIdAndDin(locationId, din))
                .thenReturn(Optional.empty());

        // Act
        List<ForecastSummaryDto> results = service.getLatestForecasts(locationId, ForecastQueryParams.defaults());

        // Assert
        ForecastSummaryDto dto = results.get(0);
        int sum = dto.graphPoints().stream().mapToInt(Integer::intValue).sum();
        assertThat(sum).isEqualTo(predictedQuantity);
        // 141 / 7 = 20 remainder 1, so first point should be 21
        assertThat(dto.graphPoints().get(0)).isEqualTo(21);
    }

    private Forecast createForecast(String din, UUID locationId, int horizonDays, int predictedQuantity, Instant generatedAt) {
        Forecast forecast = new Forecast();
        forecast.setDin(din);
        forecast.setLocationId(locationId);
        forecast.setForecastHorizonDays(horizonDays);
        forecast.setPredictedQuantity(predictedQuantity);
        forecast.setConfidence(ForecastConfidence.high);
        forecast.setDaysOfSupply(new BigDecimal("4.5"));
        forecast.setReorderStatus(ReorderStatus.amber);
        forecast.setModelPath("test_model");
        forecast.setGeneratedAt(generatedAt);
        forecast.setAvgDailyDemand(new BigDecimal("20"));
        forecast.setProphetLower(new BigDecimal("130"));
        forecast.setProphetUpper(new BigDecimal("150"));
        forecast.setIsOutdated(false);
        return forecast;
    }
}
