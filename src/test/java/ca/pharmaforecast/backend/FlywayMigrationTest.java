package ca.pharmaforecast.backend;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class FlywayMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("pharmaforecast")
            .withUsername("pharmaforecast")
            .withPassword("pharmaforecast");

    @Test
    void productionMigrationsApplyToPostgres() {
        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load();

        MigrateResult result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(1);
    }

    @Test
    void bootstrapFunctionCreatesFirstOwnerTenantShape() {
        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        );
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        UUID authUserId = UUID.randomUUID();

        Integer rowCount = jdbcTemplate.queryForObject(
                """
                        SELECT count(*)
                        FROM bootstrap_first_owner_user(?, ?, ?, ?, ?)
                        """,
                Integer.class,
                authUserId,
                "OWNER@EXAMPLE.COM",
                "Main Pharmacy",
                "100 Bank St, Ottawa, ON",
                "100 Bank St, Ottawa, ON"
        );

        assertThat(rowCount).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT role FROM app_users WHERE id = ?",
                String.class,
                authUserId
        )).isEqualTo("owner");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT email FROM app_users WHERE id = ?",
                String.class,
                authUserId
        )).isEqualTo("owner@example.com");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notification_settings",
                Integer.class
        )).isEqualTo(1);
    }

    @Test
    void bootstrapFunctionIsIdempotentForExistingAuthUser() {
        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        );
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        UUID authUserId = UUID.randomUUID();

        UUID firstOrganizationId = bootstrap(jdbcTemplate, authUserId, "owner@example.com")
                .organizationId();
        BootstrapRow secondBootstrap = bootstrap(jdbcTemplate, authUserId, "owner@example.com");

        assertThat(secondBootstrap.organizationId()).isEqualTo(firstOrganizationId);
        assertThat(secondBootstrap.userId()).isEqualTo(authUserId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM app_users WHERE id = ?",
                Integer.class,
                authUserId
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM organizations",
                Integer.class
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM notification_settings",
                Integer.class
        )).isEqualTo(1);
    }

    private BootstrapRow bootstrap(JdbcTemplate jdbcTemplate, UUID authUserId, String email) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT organization_id, location_id, user_id
                        FROM bootstrap_first_owner_user(?, ?, ?, ?, ?)
                        """,
                (rs, rowNum) -> new BootstrapRow(
                        rs.getObject("organization_id", UUID.class),
                        rs.getObject("location_id", UUID.class),
                        rs.getObject("user_id", UUID.class)
                ),
                authUserId,
                email,
                "Main Pharmacy",
                "Main Pharmacy - Bank",
                "100 Bank St, Ottawa, ON"
        );
    }

    private record BootstrapRow(UUID organizationId, UUID locationId, UUID userId) {
    }
}
