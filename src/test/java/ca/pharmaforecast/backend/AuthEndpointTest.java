package ca.pharmaforecast.backend;

import ca.pharmaforecast.backend.auth.User;
import ca.pharmaforecast.backend.auth.UserRole;
import ca.pharmaforecast.backend.location.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class AuthEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void resetRepositories() {
        AuthTestRepositoryConfig.reset();
    }

    @Test
    void protectedRoutesRequireBearerToken() throws Exception {
        mockMvc.perform(get("/drugs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void currentUserRequiresAuthenticatedContext() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void currentUserReturnsDatabaseBackedPrincipalAndActiveLocations() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        User user = user(userId, organizationId, "owner@example.com", UserRole.owner);
        Location location = location(locationId, organizationId, "Main Pharmacy", "100 Bank St, Ottawa, ON");

        AuthTestRepositoryConfig.putUser(userId, "owner@example.com", user);
        AuthTestRepositoryConfig.putLocations(organizationId, List.of(location));

        mockMvc.perform(get("/auth/me").with(supabaseJwt(userId, "owner@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("owner@example.com"))
                .andExpect(jsonPath("$.role").value("owner"))
                .andExpect(jsonPath("$.organization_id").value(organizationId.toString()))
                .andExpect(jsonPath("$.locations[0].id").value(locationId.toString()))
                .andExpect(jsonPath("$.locations[0].name").value("Main Pharmacy"))
                .andExpect(jsonPath("$.locations[0].address").value("100 Bank St, Ottawa, ON"));
    }

    @Test
    void validJwtWithoutAppUserReturnsBootstrapRequired() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/auth/me").with(supabaseJwt(userId, "new@example.com")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("USER_PROFILE_NOT_BOOTSTRAPPED"));
    }

    @Test
    void logoutIsStatelessAcknowledgement() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isNoContent());
    }

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor supabaseJwt(UUID userId, String email) {
        return jwt().jwt(token -> token
                .subject(userId.toString())
                .claim("email", email)
                .audience(List.of("authenticated")));
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
}
