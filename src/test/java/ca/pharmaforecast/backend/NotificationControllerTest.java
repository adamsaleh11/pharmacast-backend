package ca.pharmaforecast.backend;

import ca.pharmaforecast.backend.notification.NotificationSettingsResponse;
import ca.pharmaforecast.backend.notification.NotificationService;
import ca.pharmaforecast.backend.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ca.pharmaforecast.backend.notification.NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private UserRepository userRepository;

    @Test
    void updateSettingsReturnsOrganizationNotificationPreferences() throws Exception {
        UUID organizationId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        when(notificationService.updateSettings(eq(organizationId), any()))
                .thenReturn(new NotificationSettingsResponse(
                        organizationId,
                        false,
                        true,
                        false
                ));

        mockMvc.perform(put("/organizations/{orgId}/notification-settings", organizationId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "daily_digest_enabled": false,
                                  "weekly_insights_enabled": true,
                                  "critical_alerts_enabled": false
                                }
                                """)
                        .with(jwt().jwt(token -> token
                                .subject("22222222-2222-2222-2222-222222222222")
                                .claim("email", "owner@example.com")
                                .audience(List.of("authenticated")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organization_id").value(organizationId.toString()))
                .andExpect(jsonPath("$.daily_digest_enabled").value(false))
                .andExpect(jsonPath("$.weekly_insights_enabled").value(true))
                .andExpect(jsonPath("$.critical_alerts_enabled").value(false));
    }

    @Test
    void getSettingsReturnsOrganizationNotificationPreferences() throws Exception {
        UUID organizationId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        when(notificationService.getSettings(organizationId))
                .thenReturn(new NotificationSettingsResponse(
                        organizationId,
                        false,
                        true,
                        false
                ));

        mockMvc.perform(get("/organizations/{orgId}/notification-settings", organizationId)
                        .with(jwt().jwt(token -> token
                                .subject("22222222-2222-2222-2222-222222222222")
                                .claim("email", "owner@example.com")
                                .audience(List.of("authenticated")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organization_id").value(organizationId.toString()))
                .andExpect(jsonPath("$.daily_digest_enabled").value(false))
                .andExpect(jsonPath("$.weekly_insights_enabled").value(true))
                .andExpect(jsonPath("$.critical_alerts_enabled").value(false));
    }
}
