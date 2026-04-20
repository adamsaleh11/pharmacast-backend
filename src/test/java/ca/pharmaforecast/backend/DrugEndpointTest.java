package ca.pharmaforecast.backend;

import ca.pharmaforecast.backend.auth.User;
import ca.pharmaforecast.backend.auth.UserRole;
import ca.pharmaforecast.backend.drug.Drug;
import ca.pharmaforecast.backend.drug.DrugStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
class DrugEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void resetRepositories() {
        AuthTestRepositoryConfig.reset();
    }

    @Test
    void authenticatedUserCanReadGlobalDrugMetadataByCanonicalDin() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        AuthTestRepositoryConfig.putUser(
                userId,
                "owner@example.com",
                user(userId, organizationId, "owner@example.com", UserRole.owner)
        );
        AuthTestRepositoryConfig.putDrug(drug("00012345"));

        mockMvc.perform(get("/drugs/{din}", "12345")
                        .with(supabaseJwt(userId, "owner@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.din").value("00012345"))
                .andExpect(jsonPath("$.name").value("ATORVASTATIN"))
                .andExpect(jsonPath("$.strength").value("20 MG"))
                .andExpect(jsonPath("$.form").value("TABLET"))
                .andExpect(jsonPath("$.therapeuticClass").value("LIPID MODIFYING AGENTS"))
                .andExpect(jsonPath("$.manufacturer").value("APOTEX INC"))
                .andExpect(jsonPath("$.status").value("MARKETED"))
                .andExpect(jsonPath("$.lastRefreshedAt").value("2026-04-20T12:00:00Z"));
    }

    @Test
    void invalidDinIsRejected() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        AuthTestRepositoryConfig.putUser(
                userId,
                "owner@example.com",
                user(userId, organizationId, "owner@example.com", UserRole.owner)
        );

        mockMvc.perform(get("/drugs/{din}", "ABC12345")
                        .with(supabaseJwt(userId, "owner@example.com")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownDinReturnsNotFound() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        AuthTestRepositoryConfig.putUser(
                userId,
                "owner@example.com",
                user(userId, organizationId, "owner@example.com", UserRole.owner)
        );

        mockMvc.perform(get("/drugs/{din}", "00099999")
                        .with(supabaseJwt(userId, "owner@example.com")))
                .andExpect(status().isNotFound());
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

    private Drug drug(String din) throws Exception {
        Drug drug = ReflectionUtils.accessibleConstructor(Drug.class).newInstance();
        drug.setDin(din);
        drug.setName("ATORVASTATIN");
        drug.setStrength("20 MG");
        drug.setForm("TABLET");
        drug.setTherapeuticClass("LIPID MODIFYING AGENTS");
        drug.setManufacturer("APOTEX INC");
        drug.setStatus(DrugStatus.MARKETED);
        drug.setLastRefreshedAt(Instant.parse("2026-04-20T12:00:00Z"));
        return drug;
    }
}
