package ca.pharmaforecast.backend;

import ca.pharmaforecast.backend.auth.User;
import ca.pharmaforecast.backend.auth.UserRole;
import ca.pharmaforecast.backend.insights.InsightsService;
import ca.pharmaforecast.backend.insights.SavingsInsightsResponse;
import ca.pharmaforecast.backend.location.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class InsightsEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InsightsService insightsService;

    @BeforeEach
    void resetRepositories() {
        AuthTestRepositoryConfig.reset();
    }

    @Test
    void savingsEndpointReturnsInsightsForOwnedLocation() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        AuthTestRepositoryConfig.putUser(userId, "owner@example.com", user(userId, organizationId, "owner@example.com", UserRole.owner));
        AuthTestRepositoryConfig.putLocations(organizationId, List.of(location(locationId, organizationId, "Main Pharmacy", "100 Bank St, Ottawa, ON")));
        when(insightsService.calculateSavings(locationId, 30)).thenReturn(new SavingsInsightsResponse(
                30,
                new BigDecimal("180.00"),
                new SavingsInsightsResponse.OverstockAvoided(new BigDecimal("30.00"), false),
                new SavingsInsightsResponse.WasteEliminated(null, true),
                new SavingsInsightsResponse.StockoutsPrevented(1, new BigDecimal("150.00")),
                true,
                "Some savings categories require additional cost, upload, or forecast history data."
        ));

        mockMvc.perform(get("/locations/{locationId}/insights/savings?period=30", locationId)
                        .with(supabaseJwt(userId, "owner@example.com"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period_days").value(30))
                .andExpect(jsonPath("$.total_savings").value(180.00))
                .andExpect(jsonPath("$.overstock_avoided.value").value(30.00))
                .andExpect(jsonPath("$.overstock_avoided.requires_cost_data").value(false))
                .andExpect(jsonPath("$.waste_eliminated.value").doesNotExist())
                .andExpect(jsonPath("$.waste_eliminated.requires_multiple_uploads").value(true))
                .andExpect(jsonPath("$.stockouts_prevented.count").value(1))
                .andExpect(jsonPath("$.stockouts_prevented.estimated_value").value(150.00))
                .andExpect(jsonPath("$.insufficient_data").value(true));
    }

    @Test
    void savingsEndpointRejectsLocationOutsideUsersOrganization() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID otherOrganizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        AuthTestRepositoryConfig.putUser(userId, "owner@example.com", user(userId, organizationId, "owner@example.com", UserRole.owner));
        AuthTestRepositoryConfig.putLocations(otherOrganizationId, List.of(location(locationId, otherOrganizationId, "Other Pharmacy", "200 Bank St, Ottawa, ON")));

        mockMvc.perform(get("/locations/{locationId}/insights/savings?period=30", locationId)
                        .with(supabaseJwt(userId, "owner@example.com")))
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
