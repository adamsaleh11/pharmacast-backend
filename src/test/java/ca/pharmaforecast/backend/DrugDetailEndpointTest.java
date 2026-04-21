package ca.pharmaforecast.backend;

import ca.pharmaforecast.backend.auth.User;
import ca.pharmaforecast.backend.auth.UserRole;
import ca.pharmaforecast.backend.currentstock.CurrentStock;
import ca.pharmaforecast.backend.currentstock.CurrentStockRepository;
import ca.pharmaforecast.backend.dispensing.DispensingRecord;
import ca.pharmaforecast.backend.dispensing.DispensingRecordRepository;
import ca.pharmaforecast.backend.drug.Drug;
import ca.pharmaforecast.backend.drug.DrugRepository;
import ca.pharmaforecast.backend.drug.DrugStatus;
import ca.pharmaforecast.backend.forecast.DrugThreshold;
import ca.pharmaforecast.backend.forecast.DrugThresholdRepository;
import ca.pharmaforecast.backend.forecast.Forecast;
import ca.pharmaforecast.backend.forecast.ForecastConfidence;
import ca.pharmaforecast.backend.forecast.ForecastRepository;
import ca.pharmaforecast.backend.forecast.ReorderStatus;
import ca.pharmaforecast.backend.forecast.StockAdjustment;
import ca.pharmaforecast.backend.forecast.StockAdjustmentRepository;
import ca.pharmaforecast.backend.location.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        properties = {
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
        }
)
@AutoConfigureMockMvc
@Import(AuthTestRepositoryConfig.class)
class DrugDetailEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CurrentStockRepository currentStockRepository;

    @Autowired
    private ForecastRepository forecastRepository;

    @Autowired
    private DrugThresholdRepository drugThresholdRepository;

    @Autowired
    private DispensingRecordRepository dispensingRecordRepository;

    @Autowired
    private StockAdjustmentRepository stockAdjustmentRepository;

    @MockBean
    private Clock clock;

    @BeforeEach
    void resetRepositories() {
        AuthTestRepositoryConfig.reset();
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(clock.instant()).thenReturn(Instant.parse("2026-04-21T12:00:00Z"));
        when(drugThresholdRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(stockAdjustmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.clearInvocations(
                currentStockRepository,
                forecastRepository,
                drugThresholdRepository,
                dispensingRecordRepository,
                stockAdjustmentRepository
        );
    }

    @Test
    void detailEndpointReturnsAssembledDrugPanelDataForOwnedLocation() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();

        AuthTestRepositoryConfig.putUser(
                userId,
                "owner@example.com",
                user(userId, organizationId, "owner@example.com", UserRole.owner)
        );
        AuthTestRepositoryConfig.putLocations(organizationId, List.of(location(locationId, organizationId, "Main Pharmacy", "100 Bank St, Ottawa, ON")));
        AuthTestRepositoryConfig.putDrug(drug("00012345"));
        when(currentStockRepository.findByLocationIdAndDin(locationId, "00012345")).thenReturn(Optional.of(currentStock(locationId, "00012345", 24, Instant.parse("2026-04-20T09:15:00Z"))));
        when(forecastRepository.findTopByLocationIdAndDinOrderByGeneratedAtDesc(locationId, "00012345")).thenReturn(Optional.of(forecast(locationId, "00012345")));
        when(drugThresholdRepository.findByLocationIdAndDin(locationId, "00012345")).thenReturn(Optional.of(threshold(locationId, "00012345")));
        when(dispensingRecordRepository.findByLocationIdAndDin(locationId, "00012345")).thenReturn(List.of(
                dispensingRecord(locationId, "00012345", LocalDate.of(2026, 4, 20), 8),
                dispensingRecord(locationId, "00012345", LocalDate.of(2026, 4, 14), 6)
        ));
        when(stockAdjustmentRepository.findByLocationIdAndDin(locationId, "00012345")).thenReturn(List.of(
                stockAdjustment(locationId, "00012345", 3, Instant.parse("2026-04-19T10:00:00Z"), "Cycle count correction"),
                stockAdjustment(locationId, "00012345", -1, Instant.parse("2026-04-18T10:00:00Z"), "Damaged pack removed")
        ));

        mockMvc.perform(get("/locations/{locationId}/drugs/{din}/detail", locationId, "00012345")
                        .with(jwt().jwt(token -> token
                                .subject(userId.toString())
                                .claim("email", "owner@example.com")
                                .audience(List.of("authenticated")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.drug.din").value("00012345"))
                .andExpect(jsonPath("$.drug.name").value("ATORVASTATIN"))
                .andExpect(jsonPath("$.current_stock").value(24))
                .andExpect(jsonPath("$.stock_last_updated").value("2026-04-20T09:15:00Z"))
                .andExpect(jsonPath("$.latest_forecast.din").value("00012345"))
                .andExpect(jsonPath("$.latest_forecast.predicted_quantity").value(12))
                .andExpect(jsonPath("$.latest_forecast.model_path").value("xgboost_residual_interval"))
                .andExpect(jsonPath("$.threshold.lead_time_days").value(2))
                .andExpect(jsonPath("$.threshold.notifications_enabled").value(true))
                .andExpect(jsonPath("$.dispensing_history[0].week").value("2026-04-20"))
                .andExpect(jsonPath("$.dispensing_history[0].quantity").value(8))
                .andExpect(jsonPath("$.stock_adjustments[0].adjustment_quantity").value(3))
                .andExpect(jsonPath("$.stock_adjustments[0].note").value("Cycle count correction"));
    }

    @Test
    void detailEndpointReturnsLocationDetailEvenWhenDrugCatalogRowIsMissing() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        String din = "02525356";

        AuthTestRepositoryConfig.putUser(
                userId,
                "owner@example.com",
                user(userId, organizationId, "owner@example.com", UserRole.owner)
        );
        AuthTestRepositoryConfig.putLocations(organizationId, List.of(location(locationId, organizationId, "Main Pharmacy", "100 Bank St, Ottawa, ON")));

        when(currentStockRepository.findByLocationIdAndDin(locationId, din))
                .thenReturn(Optional.of(currentStock(locationId, din, 17, Instant.parse("2026-04-20T09:15:00Z"))));
        when(forecastRepository.findTopByLocationIdAndDinOrderByGeneratedAtDesc(locationId, din))
                .thenReturn(Optional.of(forecast(locationId, din)));
        when(drugThresholdRepository.findByLocationIdAndDin(locationId, din))
                .thenReturn(Optional.of(threshold(locationId, din)));
        when(dispensingRecordRepository.findByLocationIdAndDin(locationId, din))
                .thenReturn(List.of(dispensingRecord(locationId, din, LocalDate.of(2026, 4, 20), 8)));

        mockMvc.perform(get("/locations/{locationId}/drugs/{din}/detail", locationId, din)
                        .with(jwt().jwt(token -> token
                                .subject(userId.toString())
                                .claim("email", "owner@example.com")
                                .audience(List.of("authenticated")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.drug.din").value(din))
                .andExpect(jsonPath("$.drug.name").value("UNKNOWN"))
                .andExpect(jsonPath("$.drug.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.current_stock").value(17))
                .andExpect(jsonPath("$.latest_forecast.model_path").value("xgboost_residual_interval"));
    }

    @Test
    void thresholdUpsertCreatesDefaultsWhenFieldsAreOmitted() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();

        AuthTestRepositoryConfig.putUser(
                userId,
                "owner@example.com",
                user(userId, organizationId, "owner@example.com", UserRole.owner)
        );
        AuthTestRepositoryConfig.putLocations(organizationId, List.of(location(locationId, organizationId, "Main Pharmacy", "100 Bank St, Ottawa, ON")));
        AuthTestRepositoryConfig.putDrug(drug("00012345"));

        mockMvc.perform(put("/locations/{locationId}/drugs/{din}/threshold", locationId, "00012345")
                        .with(jwt().jwt(token -> token
                                .subject(userId.toString())
                                .claim("email", "owner@example.com")
                                .audience(List.of("authenticated"))))
                        .contentType("application/json")
                        .content("""
                                {
                                  "notifications_enabled": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lead_time_days").value(2))
                .andExpect(jsonPath("$.red_threshold_days").value(3))
                .andExpect(jsonPath("$.amber_threshold_days").value(7))
                .andExpect(jsonPath("$.safety_multiplier").value("BALANCED"))
                .andExpect(jsonPath("$.notifications_enabled").value(false));
    }

    @Test
    void adjustmentEndpointCreatesAuditOnlyRecordAndAllowsNegativeQuantities() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();

        AuthTestRepositoryConfig.putUser(
                userId,
                "owner@example.com",
                user(userId, organizationId, "owner@example.com", UserRole.owner)
        );
        AuthTestRepositoryConfig.putLocations(organizationId, List.of(location(locationId, organizationId, "Main Pharmacy", "100 Bank St, Ottawa, ON")));
        AuthTestRepositoryConfig.putDrug(drug("00012345"));

        mockMvc.perform(post("/locations/{locationId}/drugs/{din}/adjust", locationId, "00012345")
                        .with(jwt().jwt(token -> token
                                .subject(userId.toString())
                                .claim("email", "owner@example.com")
                                .audience(List.of("authenticated"))))
                        .contentType("application/json")
                        .content("""
                                {
                                  "adjustment_quantity": -2,
                                  "note": "Broken box removed"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adjustment.adjustment_quantity").value(-2))
                .andExpect(jsonPath("$.adjustment.note").value("Broken box removed"))
                .andExpect(jsonPath("$.adjustment.adjusted_at").value("2026-04-21T12:00:00Z"));

        verifyNoInteractions(currentStockRepository);
    }

    @Test
    void thresholdUpsertPreservesExistingValuesWhenFieldsAreOmitted() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();

        AuthTestRepositoryConfig.putUser(
                userId,
                "owner@example.com",
                user(userId, organizationId, "owner@example.com", UserRole.owner)
        );
        AuthTestRepositoryConfig.putLocations(organizationId, List.of(location(locationId, organizationId, "Main Pharmacy", "100 Bank St, Ottawa, ON")));
        AuthTestRepositoryConfig.putDrug(drug("00012345"));

        DrugThreshold existing = threshold(locationId, "00012345");
        ReflectionTestUtils.setField(existing, "leadTimeDays", 5);
        ReflectionTestUtils.setField(existing, "redThresholdDays", 4);
        ReflectionTestUtils.setField(existing, "amberThresholdDays", 9);
        ReflectionTestUtils.setField(existing, "safetyMultiplier", ca.pharmaforecast.backend.forecast.SafetyMultiplier.aggressive);
        ReflectionTestUtils.setField(existing, "notificationsEnabled", false);
        when(drugThresholdRepository.findByLocationIdAndDin(locationId, "00012345")).thenReturn(Optional.of(existing));

        mockMvc.perform(put("/locations/{locationId}/drugs/{din}/threshold", locationId, "00012345")
                        .with(jwt().jwt(token -> token
                                .subject(userId.toString())
                                .claim("email", "owner@example.com")
                                .audience(List.of("authenticated"))))
                        .contentType("application/json")
                        .content("""
                                {
                                  "lead_time_days": 6
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lead_time_days").value(6))
                .andExpect(jsonPath("$.red_threshold_days").value(4))
                .andExpect(jsonPath("$.amber_threshold_days").value(9))
                .andExpect(jsonPath("$.safety_multiplier").value("AGGRESSIVE"))
                .andExpect(jsonPath("$.notifications_enabled").value(false));
    }

    private User user(UUID id, UUID organizationId, String email, UserRole role) throws Exception {
        User user = ReflectionUtils.accessibleConstructor(User.class).newInstance();
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "organizationId", organizationId);
        ReflectionTestUtils.setField(user, "email", email);
        ReflectionTestUtils.setField(user, "role", role);
        return user;
    }

    private Location location(UUID id, UUID organizationId, String name, String address) throws Exception {
        Location location = ReflectionUtils.accessibleConstructor(Location.class).newInstance();
        ReflectionTestUtils.setField(location, "id", id);
        ReflectionTestUtils.setField(location, "organizationId", organizationId);
        ReflectionTestUtils.setField(location, "name", name);
        ReflectionTestUtils.setField(location, "address", address);
        return location;
    }

    private Drug drug(String din) throws Exception {
        Drug drug = ReflectionUtils.accessibleConstructor(Drug.class).newInstance();
        ReflectionTestUtils.setField(drug, "din", din);
        ReflectionTestUtils.setField(drug, "name", "ATORVASTATIN");
        ReflectionTestUtils.setField(drug, "strength", "20 MG");
        ReflectionTestUtils.setField(drug, "form", "TABLET");
        ReflectionTestUtils.setField(drug, "therapeuticClass", "LIPID MODIFYING AGENTS");
        ReflectionTestUtils.setField(drug, "manufacturer", "APOTEX INC");
        ReflectionTestUtils.setField(drug, "status", DrugStatus.MARKETED);
        ReflectionTestUtils.setField(drug, "lastRefreshedAt", Instant.parse("2026-04-20T12:00:00Z"));
        return drug;
    }

    private CurrentStock currentStock(UUID locationId, String din, int quantity, Instant updatedAt) throws Exception {
        CurrentStock stock = ReflectionUtils.accessibleConstructor(CurrentStock.class).newInstance();
        ReflectionTestUtils.setField(stock, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(stock, "locationId", locationId);
        ReflectionTestUtils.setField(stock, "din", din);
        ReflectionTestUtils.setField(stock, "quantity", quantity);
        ReflectionTestUtils.setField(stock, "updatedAt", updatedAt.atOffset(ZoneOffset.UTC));
        return stock;
    }

    private Forecast forecast(UUID locationId, String din) throws Exception {
        Forecast forecast = ReflectionUtils.accessibleConstructor(Forecast.class).newInstance();
        ReflectionTestUtils.setField(forecast, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(forecast, "locationId", locationId);
        ReflectionTestUtils.setField(forecast, "din", din);
        ReflectionTestUtils.setField(forecast, "generatedAt", Instant.parse("2026-04-20T12:00:00Z"));
        ReflectionTestUtils.setField(forecast, "forecastHorizonDays", 7);
        ReflectionTestUtils.setField(forecast, "predictedQuantity", 12);
        ReflectionTestUtils.setField(forecast, "confidence", ForecastConfidence.high);
        ReflectionTestUtils.setField(forecast, "daysOfSupply", BigDecimal.valueOf(4.5));
        ReflectionTestUtils.setField(forecast, "reorderStatus", ReorderStatus.red);
        ReflectionTestUtils.setField(forecast, "modelPath", "xgboost_residual_interval");
        ReflectionTestUtils.setField(forecast, "prophetLower", BigDecimal.valueOf(10));
        ReflectionTestUtils.setField(forecast, "prophetUpper", BigDecimal.valueOf(15));
        ReflectionTestUtils.setField(forecast, "avgDailyDemand", BigDecimal.valueOf(2.0));
        ReflectionTestUtils.setField(forecast, "reorderPoint", BigDecimal.valueOf(6.0));
        ReflectionTestUtils.setField(forecast, "dataPointsUsed", 21);
        return forecast;
    }

    private DrugThreshold threshold(UUID locationId, String din) throws Exception {
        DrugThreshold threshold = ReflectionUtils.accessibleConstructor(DrugThreshold.class).newInstance();
        ReflectionTestUtils.setField(threshold, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(threshold, "locationId", locationId);
        ReflectionTestUtils.setField(threshold, "din", din);
        ReflectionTestUtils.setField(threshold, "leadTimeDays", 2);
        ReflectionTestUtils.setField(threshold, "redThresholdDays", 3);
        ReflectionTestUtils.setField(threshold, "amberThresholdDays", 7);
        ReflectionTestUtils.setField(threshold, "safetyMultiplier", ca.pharmaforecast.backend.forecast.SafetyMultiplier.balanced);
        ReflectionTestUtils.setField(threshold, "notificationsEnabled", true);
        return threshold;
    }

    private DispensingRecord dispensingRecord(UUID locationId, String din, LocalDate date, int quantity) throws Exception {
        DispensingRecord record = ReflectionUtils.accessibleConstructor(DispensingRecord.class).newInstance();
        ReflectionTestUtils.setField(record, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(record, "locationId", locationId);
        ReflectionTestUtils.setField(record, "din", din);
        ReflectionTestUtils.setField(record, "dispensedDate", date);
        ReflectionTestUtils.setField(record, "quantityDispensed", quantity);
        return record;
    }

    private StockAdjustment stockAdjustment(UUID locationId, String din, int quantity, Instant adjustedAt, String note) throws Exception {
        StockAdjustment adjustment = ReflectionUtils.accessibleConstructor(StockAdjustment.class).newInstance();
        ReflectionTestUtils.setField(adjustment, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(adjustment, "locationId", locationId);
        ReflectionTestUtils.setField(adjustment, "din", din);
        ReflectionTestUtils.setField(adjustment, "adjustmentQuantity", quantity);
        ReflectionTestUtils.setField(adjustment, "adjustedAt", adjustedAt);
        ReflectionTestUtils.setField(adjustment, "note", note);
        return adjustment;
    }
}
