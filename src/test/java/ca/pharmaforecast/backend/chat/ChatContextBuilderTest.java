package ca.pharmaforecast.backend.chat;

import ca.pharmaforecast.backend.dispensing.DispensingRecord;
import ca.pharmaforecast.backend.dispensing.DispensingRecordRepository;
import ca.pharmaforecast.backend.drug.Drug;
import ca.pharmaforecast.backend.drug.DrugRepository;
import ca.pharmaforecast.backend.forecast.Forecast;
import ca.pharmaforecast.backend.forecast.ForecastConfidence;
import ca.pharmaforecast.backend.forecast.ForecastRepository;
import ca.pharmaforecast.backend.forecast.ReorderStatus;
import ca.pharmaforecast.backend.insights.InsightsService;
import ca.pharmaforecast.backend.location.Location;
import ca.pharmaforecast.backend.location.LocationRepository;
import ca.pharmaforecast.backend.notification.Notification;
import ca.pharmaforecast.backend.notification.NotificationRepository;
import ca.pharmaforecast.backend.notification.NotificationType;
import ca.pharmaforecast.backend.organization.Organization;
import ca.pharmaforecast.backend.organization.OrganizationRepository;
import org.junit.jupiter.api.Test;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatContextBuilderTest {

    @Test
    void buildSystemPromptUsesFreshLocationAndInventoryContextWithoutPatientData() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        String dinRed = "12345678";
        String dinAmber = "87654321";
        String dinGreen = "11112222";
        String dinTopVolume = "22223333";

        OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
        LocationRepository locationRepository = mock(LocationRepository.class);
        ForecastRepository forecastRepository = mock(ForecastRepository.class);
        DrugRepository drugRepository = mock(DrugRepository.class);
        DispensingRecordRepository dispensingRecordRepository = mock(DispensingRecordRepository.class);
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        InsightsService insightsService = mock(InsightsService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-21T00:00:00Z"), ZoneOffset.UTC);

        when(organizationRepository.findById(organizationId)).thenReturn(Optional.of(organization(organizationId, "Main Org")));
        when(locationRepository.findById(locationId)).thenReturn(Optional.of(location(locationId, organizationId, "Downtown Pharmacy")));
        when(forecastRepository.findByLocationIdOrderByGeneratedAtDesc(locationId)).thenReturn(List.of(
                forecast(locationId, dinRed, Instant.parse("2026-04-21T10:00:00Z"), "RED", 40, 2.5, 14),
                forecast(locationId, dinRed, Instant.parse("2026-04-20T10:00:00Z"), "GREEN", 20, 3.0, 7),
                forecast(locationId, dinAmber, Instant.parse("2026-04-21T09:00:00Z"), "AMBER", 18, 4.0, 14),
                forecast(locationId, dinGreen, Instant.parse("2026-04-21T08:00:00Z"), "GREEN", 15, 5.0, 14)
        ));
        when(drugRepository.findByDinIn(anyList())).thenReturn(List.of(
                drug(dinRed, "Drug Red", "10 mg"),
                drug(dinAmber, "Drug Amber", "20 mg"),
                drug(dinGreen, "Drug Green", "30 mg"),
                drug(dinTopVolume, "Drug Volume", "40 mg")
        ));
        when(dispensingRecordRepository.findByLocationId(locationId)).thenReturn(List.of(
                dispensing(locationId, dinTopVolume, LocalDate.of(2026, 4, 1), 20, "patient-1"),
                dispensing(locationId, dinTopVolume, LocalDate.of(2026, 4, 2), 15, "patient-2"),
                dispensing(locationId, dinAmber, LocalDate.of(2026, 4, 3), 9, null),
                dispensing(locationId, dinRed, LocalDate.of(2026, 4, 4), 7, null)
        ));
        when(notificationRepository.findTop5ByOrganizationIdOrderByCreatedAtDesc(organizationId)).thenReturn(List.of(
                notification(organizationId, locationId, NotificationType.critical_reorder, Instant.parse("2026-04-21T11:00:00Z")),
                notification(organizationId, locationId, NotificationType.daily_digest, Instant.parse("2026-04-21T10:00:00Z"))
        ));
        when(insightsService.estimateMonthlySavings(locationId)).thenReturn(Optional.of(new BigDecimal("123.45")));

        ChatContextBuilder builder = new ChatContextBuilder(
                organizationRepository,
                locationRepository,
                forecastRepository,
                drugRepository,
                dispensingRecordRepository,
                notificationRepository,
                insightsService,
                clock
        );

        String prompt = builder.buildSystemPrompt(locationId, organizationId);

        assertThat(prompt).contains("AI pharmacy inventory advisor for Main Org");
        assertThat(prompt).contains("CURRENT INVENTORY SNAPSHOT (Downtown Pharmacy, as of 2026-04-21)");
        assertThat(prompt).contains("Total drugs tracked: 3");
        assertThat(prompt).contains("Critical (reorder now): 1 drugs");
        assertThat(prompt).contains("Reorder soon: 1 drugs");
        assertThat(prompt).contains("Well stocked: 1 drugs");
        assertThat(prompt).contains("DRUG FORECAST SUMMARY (14-day horizon):");
        assertThat(prompt).contains("Drug Red 10 mg: 40 units needed, 2.5 days remaining, status: RED");
        assertThat(prompt).contains("Drug Amber 20 mg: 18 units needed, 4.0 days remaining, status: AMBER");
        assertThat(prompt).contains("Drug Green 30 mg: 15 units needed, 5.0 days remaining, status: GREEN");
        assertThat(prompt).contains("TOP 10 DRUGS BY DISPENSING VOLUME (last 30 days):");
        assertThat(prompt).contains("Drug Volume 40 mg: 35 units dispensed");
        assertThat(prompt).contains("RECENT ALERTS:");
        assertThat(prompt).contains("critical_reorder alert");
        assertThat(prompt).contains("daily_digest alert");
        assertThat(prompt).doesNotContain("{}");
        assertThat(prompt).contains("ESTIMATED SAVINGS THIS MONTH: $123.45");
        assertThat(prompt).doesNotContain("patient-1");
        assertThat(prompt).contains("Never reveal patient data");
    }

    @Test
    void buildSystemPromptBoundsForecastLinesAndExcludesRawNotificationPayload() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();

        OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
        LocationRepository locationRepository = mock(LocationRepository.class);
        ForecastRepository forecastRepository = mock(ForecastRepository.class);
        DrugRepository drugRepository = mock(DrugRepository.class);
        DispensingRecordRepository dispensingRecordRepository = mock(DispensingRecordRepository.class);
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        InsightsService insightsService = mock(InsightsService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-21T00:00:00Z"), ZoneOffset.UTC);

        List<Forecast> forecasts = new java.util.ArrayList<>();
        List<Drug> drugs = new java.util.ArrayList<>();
        for (int index = 1; index <= 25; index++) {
            String din = "%08d".formatted(index);
            forecasts.add(forecast(locationId, din, Instant.parse("2026-04-21T10:00:00Z"), "RED", index, 2.0, 14));
            drugs.add(drug(din, "Drug " + index, "10 mg"));
        }

        when(organizationRepository.findById(organizationId)).thenReturn(Optional.of(organization(organizationId, "Main Org")));
        when(locationRepository.findById(locationId)).thenReturn(Optional.of(location(locationId, organizationId, "Downtown Pharmacy")));
        when(forecastRepository.findByLocationIdOrderByGeneratedAtDesc(locationId)).thenReturn(forecasts);
        when(drugRepository.findByDinIn(anyList())).thenReturn(drugs);
        when(dispensingRecordRepository.findByLocationId(locationId)).thenReturn(List.of());
        when(notificationRepository.findTop5ByOrganizationIdOrderByCreatedAtDesc(organizationId)).thenReturn(List.of(
                notification(
                        organizationId,
                        locationId,
                        NotificationType.critical_reorder,
                        Instant.parse("2026-04-21T11:00:00Z"),
                        "{\"din\":\"12345678\",\"patient_id\":\"must-not-leak\",\"quantity\":99}"
                )
        ));
        when(insightsService.estimateMonthlySavings(locationId)).thenReturn(Optional.empty());

        ChatContextBuilder builder = new ChatContextBuilder(
                organizationRepository,
                locationRepository,
                forecastRepository,
                drugRepository,
                dispensingRecordRepository,
                notificationRepository,
                insightsService,
                clock
        );

        String prompt = builder.buildSystemPrompt(locationId, organizationId);

        assertThat(prompt).contains("Drug 1 10 mg");
        assertThat(prompt).contains("Drug 20 10 mg");
        assertThat(prompt).doesNotContain("Drug 21 10 mg");
        assertThat(prompt).doesNotContain("Drug 25 10 mg");
        assertThat(prompt).contains("critical_reorder alert");
        assertThat(prompt).doesNotContain("must-not-leak");
        assertThat(prompt).doesNotContain("patient_id");
        assertThat(prompt).doesNotContain("\"quantity\"");
    }

    private Organization organization(UUID id, String name) throws Exception {
        Organization organization = org.springframework.util.ReflectionUtils.accessibleConstructor(Organization.class).newInstance();
        ReflectionTestUtils.setField(organization, "id", id);
        ReflectionTestUtils.setField(organization, "name", name);
        return organization;
    }

    private Location location(UUID id, UUID organizationId, String name) throws Exception {
        Location location = org.springframework.util.ReflectionUtils.accessibleConstructor(Location.class).newInstance();
        ReflectionTestUtils.setField(location, "id", id);
        ReflectionTestUtils.setField(location, "organizationId", organizationId);
        ReflectionTestUtils.setField(location, "name", name);
        ReflectionTestUtils.setField(location, "address", "100 Bank St, Ottawa, ON");
        return location;
    }

    private Forecast forecast(UUID locationId, String din, Instant generatedAt, String reorderStatus, int predictedQuantity, double daysOfSupply, int horizonDays) throws Exception {
        Forecast forecast = org.springframework.util.ReflectionUtils.accessibleConstructor(Forecast.class).newInstance();
        ReflectionTestUtils.setField(forecast, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(forecast, "locationId", locationId);
        ReflectionTestUtils.setField(forecast, "din", din);
        ReflectionTestUtils.setField(forecast, "generatedAt", generatedAt);
        ReflectionTestUtils.setField(forecast, "forecastHorizonDays", horizonDays);
        ReflectionTestUtils.setField(forecast, "predictedQuantity", predictedQuantity);
        ReflectionTestUtils.setField(forecast, "confidence", ForecastConfidence.high);
        ReflectionTestUtils.setField(forecast, "daysOfSupply", BigDecimal.valueOf(daysOfSupply));
        ReflectionTestUtils.setField(forecast, "reorderStatus", ReorderStatus.valueOf(reorderStatus.toLowerCase()));
        ReflectionTestUtils.setField(forecast, "modelPath", "prophet");
        ReflectionTestUtils.setField(forecast, "prophetLower", BigDecimal.valueOf(predictedQuantity - 2));
        ReflectionTestUtils.setField(forecast, "prophetUpper", BigDecimal.valueOf(predictedQuantity + 2));
        ReflectionTestUtils.setField(forecast, "avgDailyDemand", BigDecimal.valueOf(1.2));
        ReflectionTestUtils.setField(forecast, "reorderPoint", BigDecimal.valueOf(6));
        ReflectionTestUtils.setField(forecast, "dataPointsUsed", 20);
        return forecast;
    }

    private Drug drug(String din, String name, String strength) throws Exception {
        Drug drug = org.springframework.util.ReflectionUtils.accessibleConstructor(Drug.class).newInstance();
        ReflectionTestUtils.setField(drug, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(drug, "din", din);
        ReflectionTestUtils.setField(drug, "name", name);
        ReflectionTestUtils.setField(drug, "strength", strength);
        ReflectionTestUtils.setField(drug, "form", "Tablet");
        ReflectionTestUtils.setField(drug, "therapeuticClass", "Therapeutic");
        ReflectionTestUtils.setField(drug, "manufacturer", "Manufacturer");
        ReflectionTestUtils.setField(drug, "status", ca.pharmaforecast.backend.drug.DrugStatus.ACTIVE);
        ReflectionTestUtils.setField(drug, "lastRefreshedAt", Instant.parse("2026-04-21T00:00:00Z"));
        return drug;
    }

    private DispensingRecord dispensing(UUID locationId, String din, LocalDate dispensedDate, int quantityDispensed, String patientId) throws Exception {
        DispensingRecord record = org.springframework.util.ReflectionUtils.accessibleConstructor(DispensingRecord.class).newInstance();
        ReflectionTestUtils.setField(record, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(record, "locationId", locationId);
        ReflectionTestUtils.setField(record, "din", din);
        ReflectionTestUtils.setField(record, "dispensedDate", dispensedDate);
        ReflectionTestUtils.setField(record, "quantityDispensed", quantityDispensed);
        ReflectionTestUtils.setField(record, "quantityOnHand", 10);
        ReflectionTestUtils.setField(record, "costPerUnit", new BigDecimal("1.25"));
        ReflectionTestUtils.setField(record, "patientId", patientId);
        return record;
    }

    private Notification notification(UUID organizationId, UUID locationId, NotificationType type, Instant createdAt) throws Exception {
        return notification(organizationId, locationId, type, createdAt, "{}");
    }

    private Notification notification(UUID organizationId, UUID locationId, NotificationType type, Instant createdAt, String payload) throws Exception {
        Notification notification = org.springframework.util.ReflectionUtils.accessibleConstructor(Notification.class).newInstance();
        ReflectionTestUtils.setField(notification, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(notification, "organizationId", organizationId);
        ReflectionTestUtils.setField(notification, "locationId", locationId);
        ReflectionTestUtils.setField(notification, "type", type);
        ReflectionTestUtils.setField(notification, "payload", payload);
        ReflectionTestUtils.setField(notification, "createdAt", createdAt);
        return notification;
    }
}
