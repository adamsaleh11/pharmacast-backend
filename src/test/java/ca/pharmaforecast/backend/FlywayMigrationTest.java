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
    void productionMigrationsBaselineExistingFoundationSchemaAtV1() {
        Flyway initialFlyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
        initialFlyway.clean();

        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
        );
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto");
        jdbcTemplate.execute("""
                CREATE TABLE organizations (
                    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                    created_at timestamptz NOT NULL DEFAULT now(),
                    updated_at timestamptz NOT NULL DEFAULT now(),
                    name text NOT NULL,
                    stripe_customer_id text,
                    subscription_status text,
                    trial_ends_at timestamptz
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE locations (
                    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                    created_at timestamptz NOT NULL DEFAULT now(),
                    updated_at timestamptz NOT NULL DEFAULT now(),
                    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
                    name text NOT NULL,
                    address text NOT NULL,
                    deactivated_at timestamptz
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE app_users (
                    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                    created_at timestamptz NOT NULL DEFAULT now(),
                    updated_at timestamptz NOT NULL DEFAULT now(),
                    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
                    email text NOT NULL,
                    role text NOT NULL,
                    CONSTRAINT uq_app_users_email UNIQUE (email)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE notification_settings (
                    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                    created_at timestamptz NOT NULL DEFAULT now(),
                    updated_at timestamptz NOT NULL DEFAULT now(),
                    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
                    daily_digest_enabled boolean NOT NULL DEFAULT true,
                    weekly_insights_enabled boolean NOT NULL DEFAULT true,
                    critical_alerts_enabled boolean NOT NULL DEFAULT true,
                    CONSTRAINT uq_notification_settings_organization UNIQUE (organization_id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE csv_uploads (
                    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                    created_at timestamptz NOT NULL DEFAULT now(),
                    updated_at timestamptz NOT NULL DEFAULT now(),
                    location_id uuid NOT NULL REFERENCES locations(id) ON DELETE RESTRICT,
                    filename text NOT NULL,
                    status text NOT NULL,
                    error_message text,
                    row_count integer,
                    drug_count integer,
                    validation_summary jsonb,
                    uploaded_at timestamptz NOT NULL,
                    CONSTRAINT ck_csv_uploads_status CHECK (status IN ('pending', 'processing', 'completed', 'failed'))
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE dispensing_records (
                    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                    created_at timestamptz NOT NULL DEFAULT now(),
                    updated_at timestamptz NOT NULL DEFAULT now(),
                    location_id uuid NOT NULL REFERENCES locations(id) ON DELETE RESTRICT,
                    din text NOT NULL,
                    dispensed_date date NOT NULL,
                    quantity_dispensed integer NOT NULL,
                    quantity_on_hand integer NOT NULL,
                    cost_per_unit numeric(12,4),
                    patient_id text
                )
                """);

        Flyway flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load();

        MigrateResult result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND type = 'BASELINE'",
                Integer.class
        )).isEqualTo(1);
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
