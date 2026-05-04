package ca.pharmaforecast.backend.forecast;

import ca.pharmaforecast.backend.location.Location;
import ca.pharmaforecast.backend.location.LocationRepository;
import ca.pharmaforecast.backend.organization.Organization;
import ca.pharmaforecast.backend.organization.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class IndependentForecastHorizonsIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("pharmaforecast")
            .withUsername("pharmaforecast")
            .withPassword("pharmaforecast");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private ForecastService forecastService;

    @Autowired
    private ForecastRepository forecastRepository;

    @Autowired
    private ForecastReadService forecastReadService;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @MockBean
    private ForecastServiceClient forecastServiceClient;

    @MockBean
    private ForecastRequestAssembler forecastRequestAssembler;

    private UUID locationId;
    private UUID organizationId;
    private String din;

    @BeforeEach
    void setup() {
        organizationId = UUID.randomUUID();
        locationId = UUID.randomUUID();
        din = "00012345";

        Organization org = new Organization();
        ReflectionTestUtils.setField(org, "id", organizationId);
        ReflectionTestUtils.setField(org, "name", "Test Org");
        organizationRepository.save(org);

        Location location = new Location();
        ReflectionTestUtils.setField(location, "id", locationId);
        ReflectionTestUtils.setField(location, "organizationId", organizationId);
        ReflectionTestUtils.setField(location, "name", "Test Pharmacy");
        ReflectionTestUtils.setField(location, "address", "123 Main St");
        locationRepository.save(location);

        when(forecastRequestAssembler.build(eq(locationId), eq(din), eq(7)))
                .thenReturn(new ForecastRequest(locationId.toString(), din, 7, 30, 2, 1.0, 3, 7, null));
        when(forecastRequestAssembler.build(eq(locationId), eq(din), eq(14)))
                .thenReturn(new ForecastRequest(locationId.toString(), din, 14, 30, 2, 1.0, 3, 7, null));
        when(forecastRequestAssembler.build(eq(locationId), eq(din), eq(30)))
                .thenReturn(new ForecastRequest(locationId.toString(), din, 30, 30, 2, 1.0, 3, 7, null));
    }

    @Test
    void canGenerateSeparateForecastsFor7DayAnd14DayHorizons() {
        when(forecastServiceClient.generateForecast(any()))
                .thenReturn(new ForecastResult(din, locationId.toString(), 7, 12, 10, 15, "HIGH", 4.5, 2.0, "RED", "prophet", 6.0, "2026-05-03T16:15:00Z", 21));
        forecastService.generateForecast(locationId, din, 7);

        when(forecastServiceClient.generateForecast(any()))
                .thenReturn(new ForecastResult(din, locationId.toString(), 14, 25, 20, 30, "MEDIUM", 8.5, 2.5, "AMBER", "prophet", 12.0, "2026-05-03T16:15:00Z", 21));
        forecastService.generateForecast(locationId, din, 14);

        List<Forecast> allForecasts = forecastRepository.findByLocationIdAndDin(locationId, din);
        assertThat(allForecasts)
                .as("Should have two independent forecasts for 7D and 14D horizons")
                .hasSize(2)
                .extracting(Forecast::getForecastHorizonDays)
                .containsExactlyInAnyOrder(7, 14);
    }

    @Test
    void listEndpointReturnsOnlyRequestedHorizon() {
        when(forecastServiceClient.generateForecast(any()))
                .thenReturn(new ForecastResult(din, locationId.toString(), 7, 12, 10, 15, "HIGH", 4.5, 2.0, "RED", "prophet", 6.0, "2026-05-03T16:15:00Z", 21));
        forecastService.generateForecast(locationId, din, 7);

        when(forecastServiceClient.generateForecast(any()))
                .thenReturn(new ForecastResult(din, locationId.toString(), 14, 25, 20, 30, "MEDIUM", 8.5, 2.5, "AMBER", "prophet", 12.0, "2026-05-03T16:15:00Z", 21));
        forecastService.generateForecast(locationId, din, 14);

        ForecastQueryParams params7 = new ForecastQueryParams(7, null, null, null, null);
        List<ForecastSummaryDto> forecasts7 = forecastReadService.getLatestForecasts(locationId, params7);
        assertThat(forecasts7).hasSize(1);
        assertThat(forecasts7.get(0).din()).isEqualTo(din);

        ForecastQueryParams params14 = new ForecastQueryParams(14, null, null, null, null);
        List<ForecastSummaryDto> forecasts14 = forecastReadService.getLatestForecasts(locationId, params14);
        assertThat(forecasts14).hasSize(1);
        assertThat(forecasts14.get(0).din()).isEqualTo(din);
    }

    @Test
    void regeneratingHorizonUpdatesOnlyThatHorizonPreservesOthers() {
        when(forecastServiceClient.generateForecast(any()))
                .thenReturn(new ForecastResult(din, locationId.toString(), 7, 12, 10, 15, "HIGH", 4.5, 2.0, "RED", "prophet", 6.0, "2026-05-03T16:15:00Z", 21));
        forecastService.generateForecast(locationId, din, 7);

        when(forecastServiceClient.generateForecast(any()))
                .thenReturn(new ForecastResult(din, locationId.toString(), 14, 25, 20, 30, "MEDIUM", 8.5, 2.5, "AMBER", "prophet", 12.0, "2026-05-03T16:15:00Z", 21));
        forecastService.generateForecast(locationId, din, 14);

        assertThat(forecastRepository.findByLocationIdAndDin(locationId, din)).hasSize(2);

        // Regenerate 7D with different values
        when(forecastServiceClient.generateForecast(any()))
                .thenReturn(new ForecastResult(din, locationId.toString(), 7, 50, 40, 60, "LOW", 9.0, 3.0, "GREEN", "prophet", 18.0, "2026-05-04T16:15:00Z", 25));
        forecastService.generateForecast(locationId, din, 7);

        List<Forecast> allForecasts = forecastRepository.findByLocationIdAndDin(locationId, din);
        assertThat(allForecasts).hasSize(2);

        Forecast forecast7 = forecastRepository.findByLocationIdAndDinAndForecastHorizonDays(locationId, din, 7).orElseThrow();
        assertThat(forecast7.getPredictedQuantity()).isEqualTo(50);

        Forecast forecast14 = forecastRepository.findByLocationIdAndDinAndForecastHorizonDays(locationId, din, 14).orElseThrow();
        assertThat(forecast14.getPredictedQuantity()).isEqualTo(25);
    }
}
