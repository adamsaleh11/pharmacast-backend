package ca.pharmaforecast.backend.auth;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;

public class JdbcAuthBootstrapService implements AuthBootstrapService {

    private final JdbcTemplate jdbcTemplate;

    public JdbcAuthBootstrapService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public BootstrapResult bootstrapFirstOwner(BootstrapCommand command) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT organization_id, location_id, user_id
                        FROM bootstrap_first_owner_user(?, ?, ?, ?, ?)
                        """,
                (ResultSet rs, int rowNum) -> new BootstrapResult(
                        rs.getObject("organization_id", java.util.UUID.class),
                        rs.getObject("location_id", java.util.UUID.class),
                        rs.getObject("user_id", java.util.UUID.class)
                ),
                command.authUserId(),
                command.email(),
                command.organizationName(),
                command.locationName(),
                command.locationAddress()
        );
    }
}
