package ca.pharmaforecast.backend;

import ca.pharmaforecast.backend.auth.User;
import ca.pharmaforecast.backend.auth.UserRole;
import ca.pharmaforecast.backend.chat.ChatMessageResponse;
import ca.pharmaforecast.backend.chat.ChatRole;
import ca.pharmaforecast.backend.chat.ChatService;
import ca.pharmaforecast.backend.dispensing.DispensingRecordRepository;
import ca.pharmaforecast.backend.drug.DrugRepository;
import ca.pharmaforecast.backend.forecast.ForecastRepository;
import ca.pharmaforecast.backend.insights.InsightsService;
import ca.pharmaforecast.backend.location.Location;
import ca.pharmaforecast.backend.notification.NotificationRepository;
import ca.pharmaforecast.backend.organization.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
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
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatService chatService;

    @MockBean
    private OrganizationRepository organizationRepository;

    @MockBean
    private ForecastRepository forecastRepository;

    @MockBean
    private DrugRepository drugRepository;

    @MockBean
    private DispensingRecordRepository dispensingRecordRepository;

    @MockBean
    private NotificationRepository notificationRepository;

    @MockBean
    private InsightsService insightsService;

    @BeforeEach
    void resetRepositories() {
        AuthTestRepositoryConfig.reset();
    }

    @Test
    void historyEndpointReturnsMessagesForOwnedLocation() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        AuthTestRepositoryConfig.putUser(userId, "owner@example.com", user(userId, organizationId, "owner@example.com", UserRole.owner));
        AuthTestRepositoryConfig.putLocations(organizationId, List.of(location(locationId, organizationId, "Main Pharmacy", "100 Bank St, Ottawa, ON")));

        when(chatService.getHistory(locationId)).thenReturn(List.of(
                new ChatMessageResponse(UUID.randomUUID(), ChatRole.user, "Hello", Instant.parse("2026-04-21T10:00:00Z")),
                new ChatMessageResponse(UUID.randomUUID(), ChatRole.assistant, "Hi there", Instant.parse("2026-04-21T10:00:01Z"))
        ));

        mockMvc.perform(get("/locations/{locationId}/chat/history", locationId)
                        .with(jwt().jwt(token -> token
                                .subject(userId.toString())
                                .claim("email", "owner@example.com")
                                .audience(List.of("authenticated")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("Hello"))
                .andExpect(jsonPath("$[1].role").value("assistant"))
                .andExpect(jsonPath("$[1].created_at").value("2026-04-21T10:00:01Z"));
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
