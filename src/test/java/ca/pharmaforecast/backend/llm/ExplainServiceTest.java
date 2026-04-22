package ca.pharmaforecast.backend.llm;

import ca.pharmaforecast.backend.auth.AuthenticatedUserPrincipal;
import ca.pharmaforecast.backend.auth.CurrentUserService;
import ca.pharmaforecast.backend.auth.UserRole;
import ca.pharmaforecast.backend.currentstock.CurrentStock;
import ca.pharmaforecast.backend.currentstock.CurrentStockRepository;
import ca.pharmaforecast.backend.dispensing.DispensingRecord;
import ca.pharmaforecast.backend.dispensing.DispensingRecordRepository;
import ca.pharmaforecast.backend.drug.Drug;
import ca.pharmaforecast.backend.drug.DrugRepository;
import ca.pharmaforecast.backend.forecast.Forecast;
import ca.pharmaforecast.backend.forecast.ForecastConfidence;
import ca.pharmaforecast.backend.forecast.ForecastRepository;
import ca.pharmaforecast.backend.forecast.ReorderStatus;
import ca.pharmaforecast.backend.location.Location;
import ca.pharmaforecast.backend.location.LocationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExplainServiceTest {

    @Test
    void getExplanationAssemblesPayloadFromLocationForecastDrugStockAndWeeklyDispensingHistory() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        String din = "12345678";

        CurrentUserService currentUserService = mock(CurrentUserService.class);
        LocationRepository locationRepository = mock(LocationRepository.class);
        ForecastRepository forecastRepository = mock(ForecastRepository.class);
        DrugRepository drugRepository = mock(DrugRepository.class);
        DispensingRecordRepository dispensingRecordRepository = mock(DispensingRecordRepository.class);
        CurrentStockRepository currentStockRepository = mock(CurrentStockRepository.class);
        LlmServiceClient llmServiceClient = mock(LlmServiceClient.class);
        PayloadSanitizer payloadSanitizer = mock(PayloadSanitizer.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-21T00:00:00Z"), ZoneOffset.UTC);

        when(currentUserService.requireCurrentUser()).thenReturn(new AuthenticatedUserPrincipal(
                userId,
                "owner@example.com",
                organizationId,
                UserRole.owner
        ));
        when(locationRepository.findById(locationId)).thenReturn(Optional.of(location(locationId, organizationId)));
        when(forecastRepository.findTopByLocationIdAndDinOrderByGeneratedAtDesc(locationId, din)).thenReturn(Optional.of(forecast(locationId, din)));
        when(drugRepository.findByDin(din)).thenReturn(Optional.of(drug(din)));
        when(currentStockRepository.findByLocationIdAndDin(locationId, din)).thenReturn(Optional.of(currentStock(locationId, din, 15)));
        when(dispensingRecordRepository.findByLocationIdAndDin(locationId, din)).thenReturn(List.of(
                dispensing(locationId, din, LocalDate.of(2026, 3, 3), 10),
                dispensing(locationId, din, LocalDate.of(2026, 3, 10), 12)
        ));
        when(llmServiceClient.getExplanation(any())).thenReturn(new ExplanationResult("Reorder now.", Instant.parse("2026-04-21T00:00:00Z"), null));

        ExplainService service = new ExplainService(
                currentUserService,
                locationRepository,
                forecastRepository,
                drugRepository,
                dispensingRecordRepository,
                currentStockRepository,
                llmServiceClient,
                payloadSanitizer,
                clock
        );

        ExplainResponse response = service.getExplanation(locationId, din);

        assertThat(response.explanation()).isEqualTo("Reorder now.");
        assertThat(response.generatedAt()).isEqualTo(Instant.parse("2026-04-21T00:00:00Z"));

        var payloadCaptor = org.mockito.ArgumentCaptor.forClass(ExplainPayload.class);
        verify(payloadSanitizer).sanitize(payloadCaptor.capture());
        ExplainPayload payload = payloadCaptor.getValue();
        assertThat(payload.locationId()).isEqualTo(locationId);
        assertThat(payload.din()).isEqualTo(din);
        assertThat(payload.drugName()).isEqualTo("Amoxicillin");
        assertThat(payload.strength()).isEqualTo("500 mg");
        assertThat(payload.therapeuticClass()).isEqualTo("Antibiotic");
        assertThat(payload.quantityOnHand()).isEqualTo(15);
        assertThat(payload.daysOfSupply()).isEqualTo(new BigDecimal("4.5"));
        assertThat(payload.avgDailyDemand()).isEqualTo(new BigDecimal("3.2"));
        assertThat(payload.horizonDays()).isEqualTo(14);
        assertThat(payload.predictedQuantity()).isEqualTo(45);
        assertThat(payload.prophetLower()).isEqualTo(new BigDecimal("40"));
        assertThat(payload.prophetUpper()).isEqualTo(new BigDecimal("51"));
        assertThat(payload.confidence()).isEqualTo("HIGH");
        assertThat(payload.reorderStatus()).isEqualTo("RED");
        assertThat(payload.reorderPoint()).isEqualTo(new BigDecimal("12"));
        assertThat(payload.leadTimeDays()).isEqualTo(2);
        assertThat(payload.dataPointsUsed()).isEqualTo(28);
        assertThat(payload.weeklyQuantities()).containsExactly(10, 12, 0, 0, 0, 0, 0, 0);
        assertThat(payload.maxTokens()).isEqualTo(600);
        verify(llmServiceClient).getExplanation(payload);
    }

    @Test
    void getExplanationRejectsAccessOutsideUsersOrganization() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID otherOrganizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();

        CurrentUserService currentUserService = mock(CurrentUserService.class);
        LocationRepository locationRepository = mock(LocationRepository.class);
        ExplainService service = new ExplainService(
                currentUserService,
                locationRepository,
                mock(ForecastRepository.class),
                mock(DrugRepository.class),
                mock(DispensingRecordRepository.class),
                mock(CurrentStockRepository.class),
                mock(LlmServiceClient.class),
                mock(PayloadSanitizer.class),
                Clock.systemUTC()
        );

        when(currentUserService.requireCurrentUser()).thenReturn(new AuthenticatedUserPrincipal(
                userId,
                "owner@example.com",
                organizationId,
                UserRole.owner
        ));
        when(locationRepository.findById(locationId)).thenReturn(Optional.of(location(locationId, otherOrganizationId)));

        assertThatThrownBy(() -> service.getExplanation(locationId, "12345678"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getExplanationReturnsCachedResultWithoutCallingLlmAgain() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        String din = "12345678";

        CurrentUserService currentUserService = mock(CurrentUserService.class);
        LocationRepository locationRepository = mock(LocationRepository.class);
        ForecastRepository forecastRepository = mock(ForecastRepository.class);
        DrugRepository drugRepository = mock(DrugRepository.class);
        DispensingRecordRepository dispensingRecordRepository = mock(DispensingRecordRepository.class);
        CurrentStockRepository currentStockRepository = mock(CurrentStockRepository.class);
        LlmServiceClient llmServiceClient = mock(LlmServiceClient.class);
        PayloadSanitizer payloadSanitizer = mock(PayloadSanitizer.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-21T00:00:00Z"), ZoneOffset.UTC);

        when(currentUserService.requireCurrentUser()).thenReturn(new AuthenticatedUserPrincipal(
                userId,
                "owner@example.com",
                organizationId,
                UserRole.owner
        ));
        when(locationRepository.findById(locationId)).thenReturn(Optional.of(location(locationId, organizationId)));
        when(forecastRepository.findTopByLocationIdAndDinOrderByGeneratedAtDesc(locationId, din)).thenReturn(Optional.of(forecast(locationId, din)));
        when(drugRepository.findByDin(din)).thenReturn(Optional.of(drug(din)));
        when(currentStockRepository.findByLocationIdAndDin(locationId, din)).thenReturn(Optional.of(currentStock(locationId, din, 15)));
        when(dispensingRecordRepository.findByLocationIdAndDin(locationId, din)).thenReturn(List.of(dispensing(locationId, din, LocalDate.of(2026, 2, 2), 10)));
        when(llmServiceClient.getExplanation(any())).thenReturn(new ExplanationResult("Reorder now.", Instant.parse("2026-04-21T00:00:00Z"), null));

        ExplainService service = new ExplainService(
                currentUserService,
                locationRepository,
                forecastRepository,
                drugRepository,
                dispensingRecordRepository,
                currentStockRepository,
                llmServiceClient,
                payloadSanitizer,
                clock
        );

        ExplainResponse first = service.getExplanation(locationId, din);
        ExplainResponse second = service.getExplanation(locationId, din);

        assertThat(second.explanation()).isEqualTo(first.explanation());
        assertThat(second.generatedAt()).isEqualTo(first.generatedAt());
        verify(llmServiceClient, times(1)).getExplanation(any());
    }

    @Test
    void getExplanationThrowsForecastNotFoundWhenForecastIsMissing() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        String din = "12345678";

        CurrentUserService currentUserService = mock(CurrentUserService.class);
        LocationRepository locationRepository = mock(LocationRepository.class);
        ForecastRepository forecastRepository = mock(ForecastRepository.class);
        DrugRepository drugRepository = mock(DrugRepository.class);
        DispensingRecordRepository dispensingRecordRepository = mock(DispensingRecordRepository.class);
        CurrentStockRepository currentStockRepository = mock(CurrentStockRepository.class);
        LlmServiceClient llmServiceClient = mock(LlmServiceClient.class);
        PayloadSanitizer payloadSanitizer = mock(PayloadSanitizer.class);

        when(currentUserService.requireCurrentUser()).thenReturn(new AuthenticatedUserPrincipal(
                userId,
                "owner@example.com",
                organizationId,
                UserRole.owner
        ));
        when(locationRepository.findById(locationId)).thenReturn(Optional.of(location(locationId, organizationId)));
        when(forecastRepository.findTopByLocationIdAndDinOrderByGeneratedAtDesc(locationId, din)).thenReturn(Optional.empty());

        ExplainService service = new ExplainService(
                currentUserService,
                locationRepository,
                forecastRepository,
                drugRepository,
                dispensingRecordRepository,
                currentStockRepository,
                llmServiceClient,
                payloadSanitizer,
                Clock.systemUTC()
        );

        assertThatThrownBy(() -> service.getExplanation(locationId, din))
                .isInstanceOf(ForecastNotFoundException.class);
    }

    @Test
    void getExplanationThrowsStockNotSetWhenCurrentStockIsMissing() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        String din = "12345678";

        CurrentUserService currentUserService = mock(CurrentUserService.class);
        LocationRepository locationRepository = mock(LocationRepository.class);
        ForecastRepository forecastRepository = mock(ForecastRepository.class);
        DrugRepository drugRepository = mock(DrugRepository.class);
        DispensingRecordRepository dispensingRecordRepository = mock(DispensingRecordRepository.class);
        CurrentStockRepository currentStockRepository = mock(CurrentStockRepository.class);
        LlmServiceClient llmServiceClient = mock(LlmServiceClient.class);
        PayloadSanitizer payloadSanitizer = mock(PayloadSanitizer.class);

        when(currentUserService.requireCurrentUser()).thenReturn(new AuthenticatedUserPrincipal(
                userId,
                "owner@example.com",
                organizationId,
                UserRole.owner
        ));
        when(locationRepository.findById(locationId)).thenReturn(Optional.of(location(locationId, organizationId)));
        when(forecastRepository.findTopByLocationIdAndDinOrderByGeneratedAtDesc(locationId, din)).thenReturn(Optional.of(forecast(locationId, din)));
        when(drugRepository.findByDin(din)).thenReturn(Optional.of(drug(din)));
        when(currentStockRepository.findByLocationIdAndDin(locationId, din)).thenReturn(Optional.empty());

        ExplainService service = new ExplainService(
                currentUserService,
                locationRepository,
                forecastRepository,
                drugRepository,
                dispensingRecordRepository,
                currentStockRepository,
                llmServiceClient,
                payloadSanitizer,
                Clock.systemUTC()
        );

        assertThatThrownBy(() -> service.getExplanation(locationId, din))
                .isInstanceOf(ExplainStockNotSetException.class);
    }

    private Location location(UUID locationId, UUID organizationId) throws Exception {
        Location location = org.springframework.util.ReflectionUtils.accessibleConstructor(Location.class).newInstance();
        ReflectionTestUtils.setField(location, "id", locationId);
        ReflectionTestUtils.setField(location, "organizationId", organizationId);
        ReflectionTestUtils.setField(location, "name", "Main Pharmacy");
        ReflectionTestUtils.setField(location, "address", "123 Bank St, Ottawa, ON");
        return location;
    }

    private Forecast forecast(UUID locationId, String din) throws Exception {
        Forecast forecast = org.springframework.util.ReflectionUtils.accessibleConstructor(Forecast.class).newInstance();
        ReflectionTestUtils.setField(forecast, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(forecast, "locationId", locationId);
        ReflectionTestUtils.setField(forecast, "din", din);
        ReflectionTestUtils.setField(forecast, "generatedAt", Instant.parse("2026-04-20T12:00:00Z"));
        ReflectionTestUtils.setField(forecast, "forecastHorizonDays", 14);
        ReflectionTestUtils.setField(forecast, "predictedQuantity", 45);
        ReflectionTestUtils.setField(forecast, "confidence", ForecastConfidence.high);
        ReflectionTestUtils.setField(forecast, "daysOfSupply", new BigDecimal("4.5"));
        ReflectionTestUtils.setField(forecast, "reorderStatus", ReorderStatus.red);
        ReflectionTestUtils.setField(forecast, "prophetLower", new BigDecimal("40"));
        ReflectionTestUtils.setField(forecast, "prophetUpper", new BigDecimal("51"));
        ReflectionTestUtils.setField(forecast, "avgDailyDemand", new BigDecimal("3.2"));
        ReflectionTestUtils.setField(forecast, "reorderPoint", new BigDecimal("12"));
        ReflectionTestUtils.setField(forecast, "dataPointsUsed", 28);
        return forecast;
    }

    private Drug drug(String din) throws Exception {
        Drug drug = org.springframework.util.ReflectionUtils.accessibleConstructor(Drug.class).newInstance();
        ReflectionTestUtils.setField(drug, "din", din);
        ReflectionTestUtils.setField(drug, "name", "Amoxicillin");
        ReflectionTestUtils.setField(drug, "strength", "500 mg");
        ReflectionTestUtils.setField(drug, "therapeuticClass", "Antibiotic");
        return drug;
    }

    private CurrentStock currentStock(UUID locationId, String din, int quantity) throws Exception {
        CurrentStock currentStock = org.springframework.util.ReflectionUtils.accessibleConstructor(CurrentStock.class).newInstance();
        ReflectionTestUtils.setField(currentStock, "locationId", locationId);
        ReflectionTestUtils.setField(currentStock, "din", din);
        ReflectionTestUtils.setField(currentStock, "quantity", quantity);
        return currentStock;
    }

    private DispensingRecord dispensing(UUID locationId, String din, LocalDate date, int quantity) throws Exception {
        DispensingRecord dispensingRecord = org.springframework.util.ReflectionUtils.accessibleConstructor(DispensingRecord.class).newInstance();
        ReflectionTestUtils.setField(dispensingRecord, "locationId", locationId);
        ReflectionTestUtils.setField(dispensingRecord, "din", din);
        ReflectionTestUtils.setField(dispensingRecord, "dispensedDate", date);
        ReflectionTestUtils.setField(dispensingRecord, "quantityDispensed", quantity);
        return dispensingRecord;
    }
}
