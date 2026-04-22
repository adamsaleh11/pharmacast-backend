package ca.pharmaforecast.backend;

import ca.pharmaforecast.backend.auth.User;
import ca.pharmaforecast.backend.auth.UserRole;
import ca.pharmaforecast.backend.location.Location;
import ca.pharmaforecast.backend.llm.ExplainResponse;
import ca.pharmaforecast.backend.llm.ExplainService;
import ca.pharmaforecast.backend.llm.ExplainStockNotSetException;
import ca.pharmaforecast.backend.llm.ForecastNotFoundException;
import ca.pharmaforecast.backend.llm.LlmUnavailableException;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
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
class ExplainControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExplainService explainService;

    @BeforeEach
    void resetRepositories() {
        AuthTestRepositoryConfig.reset();
    }

    @Test
    void explainEndpointReturnsExplanationForAuthenticatedUser() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        String din = "12345678";
        AuthTestRepositoryConfig.putUser(userId, "owner@example.com", user(userId, organizationId, "owner@example.com", UserRole.owner));
        AuthTestRepositoryConfig.putLocations(organizationId, List.of(location(locationId, organizationId, "Main Pharmacy", "100 Bank St, Ottawa, ON")));

        when(explainService.getExplanation(any(), any())).thenReturn(new ExplainResponse(
                "Reorder now.",
                Instant.parse("2026-04-21T00:00:00Z")
        ));

        mockMvc.perform(post("/locations/{locationId}/forecasts/{din}/explain", locationId, din)
                        .with(supabaseJwt(userId, "owner@example.com"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.explanation").value("Reorder now."))
                .andExpect(jsonPath("$.generated_at").value("2026-04-21T00:00:00Z"));
    }

    @Test
    void explainEndpointReturnsForecastNotFoundEnvelope() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        String din = "12345678";
        AuthTestRepositoryConfig.putUser(userId, "owner@example.com", user(userId, organizationId, "owner@example.com", UserRole.owner));
        AuthTestRepositoryConfig.putLocations(organizationId, List.of(location(locationId, organizationId, "Main Pharmacy", "100 Bank St, Ottawa, ON")));

        when(explainService.getExplanation(any(), any())).thenThrow(new ForecastNotFoundException());

        mockMvc.perform(post("/locations/{locationId}/forecasts/{din}/explain", locationId, din)
                        .with(supabaseJwt(userId, "owner@example.com"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("FORECAST_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Generate a forecast first"));
    }

    @Test
    void explainEndpointReturnsStockNotSetEnvelope() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        String din = "12345678";
        AuthTestRepositoryConfig.putUser(userId, "owner@example.com", user(userId, organizationId, "owner@example.com", UserRole.owner));
        AuthTestRepositoryConfig.putLocations(organizationId, List.of(location(locationId, organizationId, "Main Pharmacy", "100 Bank St, Ottawa, ON")));

        when(explainService.getExplanation(any(), any())).thenThrow(new ExplainStockNotSetException());

        mockMvc.perform(post("/locations/{locationId}/forecasts/{din}/explain", locationId, din)
                        .with(supabaseJwt(userId, "owner@example.com"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("STOCK_NOT_SET"))
                .andExpect(jsonPath("$.message").value("Enter current stock before explaining"));
    }

    @Test
    void explainEndpointReturnsLlmUnavailableEnvelope() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        String din = "12345678";
        AuthTestRepositoryConfig.putUser(userId, "owner@example.com", user(userId, organizationId, "owner@example.com", UserRole.owner));
        AuthTestRepositoryConfig.putLocations(organizationId, List.of(location(locationId, organizationId, "Main Pharmacy", "100 Bank St, Ottawa, ON")));

        when(explainService.getExplanation(any(), any())).thenThrow(new LlmUnavailableException());

        mockMvc.perform(post("/locations/{locationId}/forecasts/{din}/explain", locationId, din)
                        .with(supabaseJwt(userId, "owner@example.com"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("LLM_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Try again in a moment"));
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
