package ca.pharmaforecast.backend;

import ca.pharmaforecast.backend.auth.User;
import ca.pharmaforecast.backend.auth.UserRole;
import ca.pharmaforecast.backend.location.Location;
import ca.pharmaforecast.backend.upload.CsvUpload;
import ca.pharmaforecast.backend.upload.CsvUploadStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
class CsvUploadEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void resetRepositories() {
        AuthTestRepositoryConfig.reset();
    }

    @Test
    void ownedLocationUploadReturnsPendingAndPersistsUploadShell() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        User user = user(userId, organizationId, "owner@example.com", UserRole.owner);
        Location location = location(locationId, organizationId, "Main Pharmacy", "100 Bank St, Ottawa, ON");
        AuthTestRepositoryConfig.putUser(userId, "owner@example.com", user);
        AuthTestRepositoryConfig.putLocations(organizationId, List.of(location));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "dispensing-history.csv",
                "text/csv",
                "dispensed_date,din,quantity_dispensed,quantity_on_hand\n2026-04-19,00012345,3,20\n".getBytes()
        );

        String response = mockMvc.perform(multipart("/locations/{locationId}/uploads", locationId)
                        .file(file)
                        .with(supabaseJwt(userId, "owner@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.uploadId").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID uploadId = UUID.fromString(response.replaceAll(".*\"uploadId\"\\s*:\\s*\"([^\"]+)\".*", "$1"));
        CsvUpload upload = AuthTestRepositoryConfig.upload(uploadId);
        assertThat(upload).isNotNull();
        assertThat(upload.getLocationId()).isEqualTo(locationId);
        assertThat(upload.getFilename()).isEqualTo("dispensing-history.csv");
        assertThat(upload.getStatus()).isEqualTo(CsvUploadStatus.PENDING);
        assertThat(upload.getUploadedAt()).isNotNull();
        assertThat(AuthTestRepositoryConfig.LAST_CSV_JOB).isNotNull();
        assertThat(AuthTestRepositoryConfig.LAST_CSV_JOB.uploadId()).isEqualTo(uploadId);
        assertThat(AuthTestRepositoryConfig.LAST_CSV_JOB.locationId()).isEqualTo(locationId);
        assertThat(new String(AuthTestRepositoryConfig.LAST_CSV_JOB.csvBytes())).contains("00012345");
    }

    @Test
    void uploadRejectsLocationOutsideCurrentUsersOrganization() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID otherOrganizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        User user = user(userId, organizationId, "owner@example.com", UserRole.owner);
        Location location = location(locationId, otherOrganizationId, "Other Pharmacy", "200 Bank St, Ottawa, ON");
        AuthTestRepositoryConfig.putUser(userId, "owner@example.com", user);
        AuthTestRepositoryConfig.putLocations(otherOrganizationId, List.of(location));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "dispensing-history.csv",
                "text/csv",
                "dispensed_date,din,quantity_dispensed,quantity_on_hand\n2026-04-19,00012345,3,20\n".getBytes()
        );

        mockMvc.perform(multipart("/locations/{locationId}/uploads", locationId)
                        .file(file)
                        .with(supabaseJwt(userId, "owner@example.com")))
                .andExpect(status().isForbidden());

        assertThat(AuthTestRepositoryConfig.uploadCount()).isZero();
    }

    @Test
    void ownedLocationUploadPollingReturnsRecentUploadsAndDetails() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        User user = user(userId, organizationId, "owner@example.com", UserRole.owner);
        Location location = location(locationId, organizationId, "Main Pharmacy", "100 Bank St, Ottawa, ON");
        AuthTestRepositoryConfig.putUser(userId, "owner@example.com", user);
        AuthTestRepositoryConfig.putLocations(organizationId, List.of(location));

        UUID olderUploadId = UUID.randomUUID();
        UUID newerUploadId = UUID.randomUUID();
        AuthTestRepositoryConfig.putUpload(upload(
                olderUploadId,
                locationId,
                "older.csv",
                CsvUploadStatus.SUCCESS,
                Instant.parse("2026-04-18T12:00:00Z")
        ));
        AuthTestRepositoryConfig.putUpload(upload(
                newerUploadId,
                locationId,
                "newer.csv",
                CsvUploadStatus.PENDING,
                Instant.parse("2026-04-19T12:00:00Z")
        ));

        mockMvc.perform(get("/locations/{locationId}/uploads", locationId)
                        .with(supabaseJwt(userId, "owner@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].uploadId").value(newerUploadId.toString()))
                .andExpect(jsonPath("$[0].filename").value("newer.csv"))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[1].uploadId").value(olderUploadId.toString()))
                .andExpect(jsonPath("$[1].status").value("SUCCESS"));

        mockMvc.perform(get("/locations/{locationId}/uploads/{uploadId}", locationId, olderUploadId)
                        .with(supabaseJwt(userId, "owner@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadId").value(olderUploadId.toString()))
                .andExpect(jsonPath("$.filename").value("older.csv"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
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

    private CsvUpload upload(
            UUID id,
            UUID locationId,
            String filename,
            CsvUploadStatus status,
            Instant uploadedAt
    ) throws Exception {
        CsvUpload upload = ReflectionUtils.accessibleConstructor(CsvUpload.class).newInstance();
        ReflectionTestUtils.setField(upload, "id", id);
        upload.setLocationId(locationId);
        upload.setFilename(filename);
        upload.setStatus(status);
        upload.setUploadedAt(uploadedAt);
        return upload;
    }
}
