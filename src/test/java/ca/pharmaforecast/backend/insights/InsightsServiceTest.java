package ca.pharmaforecast.backend.insights;

import ca.pharmaforecast.backend.dispensing.DispensingRecord;
import ca.pharmaforecast.backend.dispensing.DispensingRecordRepository;
import ca.pharmaforecast.backend.drug.Drug;
import ca.pharmaforecast.backend.drug.DrugStatus;
import ca.pharmaforecast.backend.drug.DrugRepository;
import ca.pharmaforecast.backend.forecast.Forecast;
import ca.pharmaforecast.backend.forecast.ForecastConfidence;
import ca.pharmaforecast.backend.forecast.ForecastRepository;
import ca.pharmaforecast.backend.forecast.ReorderStatus;
import ca.pharmaforecast.backend.forecast.StockAdjustment;
import ca.pharmaforecast.backend.forecast.StockAdjustmentRepository;
import ca.pharmaforecast.backend.upload.CsvUploadRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InsightsServiceTest {

    @Test
    void savingsUsesForecastRecommendationsPositiveStockInflowsAndStockoutReduction() {
        UUID locationId = UUID.randomUUID();
        Clock clock = Clock.fixed(Instant.parse("2026-04-24T12:00:00Z"), ZoneOffset.UTC);
        ForecastRepository forecastRepository = mock(ForecastRepository.class);
        DispensingRecordRepository dispensingRecordRepository = mock(DispensingRecordRepository.class);
        StockAdjustmentRepository stockAdjustmentRepository = mock(StockAdjustmentRepository.class);
        CsvUploadRepository csvUploadRepository = mock(CsvUploadRepository.class);

        when(forecastRepository.findByLocationIdAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(
                locationId,
                Instant.parse("2026-03-25T00:00:00Z")
        )).thenReturn(List.of(forecast(locationId, "00012345", "2026-03-20T10:00:00Z", 10)));
        when(dispensingRecordRepository.findByLocationIdAndDispensedDateBetween(
                locationId,
                LocalDate.of(2026, 2, 23),
                LocalDate.of(2026, 4, 24)
        )).thenReturn(List.of(
                dispensing(locationId, "00012345", LocalDate.of(2026, 4, 1), 2, 0, "5.00"),
                dispensing(locationId, "00012345", LocalDate.of(2026, 4, 2), 1, 4, "7.00"),
                dispensing(locationId, "00012345", LocalDate.of(2026, 3, 1), 1, 0, "6.00"),
                dispensing(locationId, "00012345", LocalDate.of(2026, 3, 2), 1, 0, "6.00"),
                dispensing(locationId, "00012345", LocalDate.of(2026, 3, 3), 1, 3, "6.00")
        ));
        when(stockAdjustmentRepository.findByLocationIdAndAdjustedAtBetween(
                locationId,
                Instant.parse("2026-03-25T00:00:00Z"),
                Instant.parse("2026-04-25T00:00:00Z")
        )).thenReturn(List.of(
                adjustment(locationId, "00012345", 15, "2026-04-10T12:00:00Z"),
                adjustment(locationId, "00012345", -2, "2026-04-11T12:00:00Z")
        ));
        when(csvUploadRepository.countByLocationIdAndStatus(locationId, ca.pharmaforecast.backend.upload.CsvUploadStatus.SUCCESS))
                .thenReturn(1L);

        InsightsService service = new InsightsService(
                forecastRepository,
                dispensingRecordRepository,
                stockAdjustmentRepository,
                csvUploadRepository,
                mock(DrugRepository.class),
                clock,
                new InsightsProperties(new BigDecimal("150"))
        );

        SavingsInsightsResponse response = service.calculateSavings(locationId, 30);

        assertThat(response.periodDays()).isEqualTo(30);
        assertThat(response.overstockAvoided().requiresCostData()).isFalse();
        assertThat(response.overstockAvoided().value()).isEqualByComparingTo("30.00");
        assertThat(response.wasteEliminated().value()).isNull();
        assertThat(response.wasteEliminated().requiresMultipleUploads()).isTrue();
        assertThat(response.stockoutsPrevented().count()).isEqualTo(1);
        assertThat(response.stockoutsPrevented().estimatedValue()).isEqualByComparingTo("150.00");
        assertThat(response.totalSavings()).isEqualByComparingTo("180.00");
        assertThat(response.insufficientData()).isTrue();
    }

    @Test
    void accuracyRequiresThreeComparablePairsAndReturnsAccuracyPercentage() {
        UUID locationId = UUID.randomUUID();
        Clock clock = Clock.fixed(Instant.parse("2026-04-24T12:00:00Z"), ZoneOffset.UTC);
        ForecastRepository forecastRepository = mock(ForecastRepository.class);
        DispensingRecordRepository dispensingRecordRepository = mock(DispensingRecordRepository.class);
        DrugRepository drugRepository = mock(DrugRepository.class);

        when(forecastRepository.findByLocationIdAndGeneratedAtBetweenOrderByGeneratedAtAsc(
                locationId,
                Instant.parse("2026-03-25T00:00:00Z"),
                Instant.parse("2026-04-25T00:00:00Z")
        )).thenReturn(List.of(
                forecast(locationId, "00012345", "2026-04-01T00:00:00Z", 7, 10),
                forecast(locationId, "00012345", "2026-04-08T00:00:00Z", 7, 20),
                forecast(locationId, "00012345", "2026-04-15T00:00:00Z", 7, 30),
                forecast(locationId, "00067890", "2026-04-01T00:00:00Z", 7, 10)
        ));
        when(dispensingRecordRepository.findByLocationIdAndDispensedDateBetween(
                locationId,
                LocalDate.of(2026, 3, 25),
                LocalDate.of(2026, 4, 24)
        )).thenReturn(List.of(
                dispensing(locationId, "00012345", LocalDate.of(2026, 4, 1), 10, 5, "1.00"),
                dispensing(locationId, "00012345", LocalDate.of(2026, 4, 8), 10, 5, "1.00"),
                dispensing(locationId, "00012345", LocalDate.of(2026, 4, 9), 10, 5, "1.00"),
                dispensing(locationId, "00012345", LocalDate.of(2026, 4, 15), 15, 5, "1.00"),
                dispensing(locationId, "00012345", LocalDate.of(2026, 4, 16), 15, 5, "1.00"),
                dispensing(locationId, "00067890", LocalDate.of(2026, 4, 1), 10, 5, "1.00")
        ));
        when(drugRepository.findByDinIn(List.of("00012345", "00067890")))
                .thenReturn(List.of(drug("00012345", "Atorvastatin"), drug("00067890", "Metformin")));

        InsightsService service = new InsightsService(
                forecastRepository,
                dispensingRecordRepository,
                mock(StockAdjustmentRepository.class),
                mock(CsvUploadRepository.class),
                drugRepository,
                clock,
                new InsightsProperties(new BigDecimal("150"))
        );

        AccuracyInsightsResponse response = service.calculateAccuracy(locationId, 30);

        assertThat(response.overallAccuracyPct()).isEqualTo(100.0);
        assertThat(response.byDrug()).hasSize(1);
        assertThat(response.byDrug().get(0).din()).isEqualTo("00012345");
        assertThat(response.byDrug().get(0).drugName()).isEqualTo("Atorvastatin");
        assertThat(response.byDrug().get(0).mape()).isEqualTo(0.0);
        assertThat(response.byDrug().get(0).forecastQty()).isEqualTo(60);
        assertThat(response.byDrug().get(0).actualQty()).isEqualTo(60);
    }

    private Forecast forecast(UUID locationId, String din, String generatedAt, int predictedQuantity) {
        return forecast(locationId, din, generatedAt, 30, predictedQuantity);
    }

    private Forecast forecast(UUID locationId, String din, String generatedAt, int horizonDays, int predictedQuantity) {
        Forecast forecast = new Forecast();
        forecast.setLocationId(locationId);
        forecast.setDin(din);
        forecast.setGeneratedAt(Instant.parse(generatedAt));
        forecast.setForecastHorizonDays(horizonDays);
        forecast.setPredictedQuantity(predictedQuantity);
        forecast.setConfidence(ForecastConfidence.high);
        forecast.setDaysOfSupply(BigDecimal.TEN);
        forecast.setReorderStatus(ReorderStatus.green);
        forecast.setModelPath("prophet");
        forecast.setProphetLower(BigDecimal.ONE);
        forecast.setProphetUpper(BigDecimal.TEN);
        return forecast;
    }

    private DispensingRecord dispensing(UUID locationId, String din, LocalDate date, int quantity, int onHand, String cost) {
        DispensingRecord record = new DispensingRecord();
        record.setLocationId(locationId);
        record.setDin(din);
        record.setDispensedDate(date);
        record.setQuantityDispensed(quantity);
        record.setQuantityOnHand(onHand);
        ReflectionTestUtils.setField(record, "costPerUnit", new BigDecimal(cost));
        return record;
    }

    private StockAdjustment adjustment(UUID locationId, String din, int quantity, String adjustedAt) {
        StockAdjustment adjustment = new StockAdjustment();
        adjustment.setLocationId(locationId);
        adjustment.setDin(din);
        adjustment.setAdjustmentQuantity(quantity);
        adjustment.setAdjustedAt(Instant.parse(adjustedAt));
        adjustment.setNote("received");
        return adjustment;
    }

    private Drug drug(String din, String name) {
        Drug drug = new Drug();
        drug.setDin(din);
        drug.setName(name);
        drug.setStrength("10 mg");
        drug.setForm("tablet");
        drug.setTherapeuticClass("statin");
        drug.setManufacturer("Example");
        drug.setStatus(DrugStatus.ACTIVE);
        drug.setLastRefreshedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return drug;
    }
}
