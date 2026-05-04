package ca.pharmaforecast.backend;

import ca.pharmaforecast.backend.auth.User;
import ca.pharmaforecast.backend.auth.UserRole;
import ca.pharmaforecast.backend.location.Location;
import ca.pharmaforecast.backend.forecast.Forecast;
import ca.pharmaforecast.backend.forecast.ForecastConfidence;
import ca.pharmaforecast.backend.forecast.ReorderStatus;
import ca.pharmaforecast.backend.forecast.ForecastService;
import ca.pharmaforecast.backend.forecast.ForecastServiceClient;
import ca.pharmaforecast.backend.forecast.ForecastResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class ForecastEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ForecastService forecastService;

    @MockBean
    private ForecastServiceClient forecastServiceClient;

    @BeforeEach
    void resetRepositories() {
        AuthTestRepositoryConfig.reset();
    }

    @Test
    void singleForecastEndpointReturnsForecastResponseForOwnedLocation() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        AuthTestRepositoryConfig.putUser(userId, "owner@example.com", user(userId, organizationId, "owner@example.com", UserRole.owner));
        AuthTestRepositoryConfig.putLocations(organizationId, List.of(location(locationId, organizationId, "Main Pharmacy", "100 Bank St, Ottawa, ON")));

        ForecastResult result = new ForecastResult(
                "00012345",
                locationId.toString(),
                7,
                12,
                10,
                15,
                "HIGH",
                4.5,
                2.0,
                "RED",
                "xgboost_residual_interval",
                6.0,
                "2026-04-20T12:00:00Z",
                21
        );
        when(forecastService.generateForecast(eq(locationId), eq("00012345"), eq(7))).thenReturn(result);

        mockMvc.perform(post("/locations/{locationId}/forecasts/generate", locationId)
                        .with(supabaseJwt(userId, "owner@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "din": "00012345",
                                  "horizon_days": 7
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.din").value("00012345"))
                .andExpect(jsonPath("$.location_id").value(locationId.toString()))
                .andExpect(jsonPath("$.predicted_quantity").value(12))
                .andExpect(jsonPath("$.confidence").value("HIGH"))
                .andExpect(jsonPath("$.reorder_status").value("RED"))
                .andExpect(jsonPath("$.model_path").value("xgboost_residual_interval"));
    }

    @Test
    void singleForecastEndpointRejectsLocationOutsideUsersOrganization() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID otherOrganizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        AuthTestRepositoryConfig.putUser(userId, "owner@example.com", user(userId, organizationId, "owner@example.com", UserRole.owner));
        AuthTestRepositoryConfig.putLocations(otherOrganizationId, List.of(location(locationId, otherOrganizationId, "Other Pharmacy", "200 Bank St, Ottawa, ON")));

        mockMvc.perform(post("/locations/{locationId}/forecasts/generate", locationId)
                        .with(supabaseJwt(userId, "owner@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "din": "00012345",
                                  "horizon_days": 7
                                }
                                """))
                .andExpect(status().isForbidden());
    }


    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor supabaseJwt(UUID userId, String email) {
        return jwt().jwt(token -> token
                .subject(userId.toString())
                .claim("email", email)
                .audience(List.of("authenticated")));
    }

    private User user(UUID id, UUID organizationId, String email, UserRole role) throws Exception {
        User user = org.springframework.util.ReflectionUtils.accessibleConstructor(User.class).newInstance();
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "organizationId", organizationId);
        ReflectionTestUtils.setField(user, "email", email);
        ReflectionTestUtils.setField(user, "role", role);
        return user;
    }

    private Location location(UUID id, UUID organizationId, String name, String address) throws Exception {
        Location location = org.springframework.util.ReflectionUtils.accessibleConstructor(Location.class).newInstance();
        ReflectionTestUtils.setField(location, "id", id);
        ReflectionTestUtils.setField(location, "organizationId", organizationId);
        ReflectionTestUtils.setField(location, "name", name);
        ReflectionTestUtils.setField(location, "address", address);
        return location;
    }
}
