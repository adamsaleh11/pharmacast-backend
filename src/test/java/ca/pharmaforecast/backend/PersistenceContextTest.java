package ca.pharmaforecast.backend;

import ca.pharmaforecast.backend.auth.UserRepository;
import ca.pharmaforecast.backend.chat.ChatMessageRepository;
import ca.pharmaforecast.backend.dispensing.DispensingRecordRepository;
import ca.pharmaforecast.backend.drug.DrugRepository;
import ca.pharmaforecast.backend.forecast.DrugThresholdRepository;
import ca.pharmaforecast.backend.forecast.ForecastRepository;
import ca.pharmaforecast.backend.forecast.StockAdjustmentRepository;
import ca.pharmaforecast.backend.location.LocationRepository;
import ca.pharmaforecast.backend.notification.NotificationRepository;
import ca.pharmaforecast.backend.notification.NotificationSettingsRepository;
import ca.pharmaforecast.backend.organization.OrganizationRepository;
import ca.pharmaforecast.backend.purchaseorder.PurchaseOrderRepository;
import ca.pharmaforecast.backend.upload.CsvUploadRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class PersistenceContextTest {

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
    private OrganizationRepository organizationRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DrugRepository drugRepository;

    @Autowired
    private DispensingRecordRepository dispensingRecordRepository;

    @Autowired
    private ForecastRepository forecastRepository;

    @Autowired
    private DrugThresholdRepository drugThresholdRepository;

    @Autowired
    private StockAdjustmentRepository stockAdjustmentRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private CsvUploadRepository csvUploadRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private NotificationSettingsRepository notificationSettingsRepository;

    @Test
    void contextStartsWithAllDomainRepositories() {
        assertThat(organizationRepository).isNotNull();
        assertThat(locationRepository).isNotNull();
        assertThat(userRepository).isNotNull();
        assertThat(drugRepository).isNotNull();
        assertThat(dispensingRecordRepository).isNotNull();
        assertThat(forecastRepository).isNotNull();
        assertThat(drugThresholdRepository).isNotNull();
        assertThat(stockAdjustmentRepository).isNotNull();
        assertThat(purchaseOrderRepository).isNotNull();
        assertThat(notificationRepository).isNotNull();
        assertThat(csvUploadRepository).isNotNull();
        assertThat(chatMessageRepository).isNotNull();
        assertThat(notificationSettingsRepository).isNotNull();
    }
}
