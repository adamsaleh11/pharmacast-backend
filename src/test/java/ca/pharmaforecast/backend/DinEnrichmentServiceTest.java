package ca.pharmaforecast.backend;

import ca.pharmaforecast.backend.drug.DinEnrichmentService;
import ca.pharmaforecast.backend.drug.DinNormalizer;
import ca.pharmaforecast.backend.drug.Drug;
import ca.pharmaforecast.backend.drug.DrugRepository;
import ca.pharmaforecast.backend.drug.DrugStatus;
import ca.pharmaforecast.backend.drug.HealthCanadaActiveIngredient;
import ca.pharmaforecast.backend.drug.HealthCanadaApiClient;
import ca.pharmaforecast.backend.drug.HealthCanadaDrugProduct;
import ca.pharmaforecast.backend.drug.HealthCanadaForm;
import ca.pharmaforecast.backend.drug.HealthCanadaProductStatus;
import ca.pharmaforecast.backend.drug.HealthCanadaSchedule;
import ca.pharmaforecast.backend.drug.HealthCanadaTherapeuticClass;
import ca.pharmaforecast.backend.dispensing.DispensingRecord;
import ca.pharmaforecast.backend.dispensing.DispensingRecordRepository;
import ca.pharmaforecast.backend.location.Location;
import ca.pharmaforecast.backend.location.LocationRepository;
import ca.pharmaforecast.backend.notification.DrugAlertEmailService;
import ca.pharmaforecast.backend.notification.Notification;
import ca.pharmaforecast.backend.notification.NotificationRepository;
import ca.pharmaforecast.backend.notification.NotificationType;
import ca.pharmaforecast.backend.auth.User;
import ca.pharmaforecast.backend.auth.UserRepository;
import ca.pharmaforecast.backend.auth.UserRole;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.ReflectionUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DinEnrichmentServiceTest {

    private final DrugRepository drugRepository = mock(DrugRepository.class);
    private final HealthCanadaApiClient healthCanadaApiClient = mock(HealthCanadaApiClient.class);
    private final DispensingRecordRepository dispensingRecordRepository = mock(DispensingRecordRepository.class);
    private final LocationRepository locationRepository = mock(LocationRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final DrugAlertEmailService drugAlertEmailService = mock(DrugAlertEmailService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-04-20T12:00:00Z"), ZoneOffset.UTC);
    private final DinEnrichmentService service = new DinEnrichmentService(
            drugRepository,
            healthCanadaApiClient,
            new DinNormalizer(),
            clock,
            dispensingRecordRepository,
            locationRepository,
            userRepository,
            notificationRepository,
            drugAlertEmailService
    );

    @Test
    void enrichesMissingDinFromHealthCanadaMetadata() {
        when(drugRepository.findByDinIn(List.of("00012345"))).thenReturn(List.of());
        when(healthCanadaApiClient.fetchDrugProduct("00012345")).thenReturn(Optional.of(new HealthCanadaDrugProduct(
                "456",
                "00012345",
                "ATORVASTATIN",
                "APOTEX INC",
                "Human"
        )));
        when(healthCanadaApiClient.fetchActiveIngredient("456")).thenReturn(Optional.of(List.of(
                new HealthCanadaActiveIngredient("ATORVASTATIN", "20", "MG", "", "")
        )));
        when(healthCanadaApiClient.fetchForm("456")).thenReturn(Optional.of(List.of(
                new HealthCanadaForm("TABLET")
        )));
        when(healthCanadaApiClient.fetchSchedule("456")).thenReturn(Optional.of(List.of(
                new HealthCanadaSchedule("Prescription")
        )));
        when(healthCanadaApiClient.fetchStatus("456")).thenReturn(Optional.of(List.of(
                new HealthCanadaProductStatus("Marketed", 2)
        )));
        when(healthCanadaApiClient.fetchTherapeuticClass("456")).thenReturn(Optional.of(List.of(
                new HealthCanadaTherapeuticClass("C10AA", "LIPID MODIFYING AGENTS")
        )));

        service.enrich(List.of("12345"));

        ArgumentCaptor<Drug> captor = ArgumentCaptor.forClass(Drug.class);
        verify(drugRepository).save(captor.capture());
        Drug saved = captor.getValue();
        assertThat(saved.getDin()).isEqualTo("00012345");
        assertThat(saved.getName()).isEqualTo("ATORVASTATIN");
        assertThat(saved.getStrength()).isEqualTo("20 MG");
        assertThat(saved.getForm()).isEqualTo("TABLET");
        assertThat(saved.getTherapeuticClass()).isEqualTo("LIPID MODIFYING AGENTS");
        assertThat(saved.getManufacturer()).isEqualTo("APOTEX INC");
        assertThat(saved.getStatus()).isEqualTo(DrugStatus.MARKETED);
        assertThat(saved.getLastRefreshedAt()).isEqualTo(Instant.parse("2026-04-20T12:00:00Z"));
    }

    @Test
    void weeklyRefreshCreatesLocationNotificationWhenDrugTransitionsToCancelled() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Drug existing = drug("00012345", "ATORVASTATIN", DrugStatus.MARKETED);
        Location location = location(locationId, organizationId);
        User owner = user(userId, organizationId, "owner@example.com", UserRole.owner);
        DispensingRecord latestRecord = dispensingRecord(locationId, "00012345", 14);

        when(drugRepository.findByLastRefreshedAtLessThanEqual(Instant.parse("2026-04-13T12:00:00Z")))
                .thenReturn(List.of(existing));
        stubCancelledHealthCanadaResponse();
        when(dispensingRecordRepository.findDistinctLocationIdsByDin("00012345")).thenReturn(List.of(locationId));
        when(locationRepository.findAllById(List.of(locationId))).thenReturn(List.of(location));
        when(dispensingRecordRepository.findTopByLocationIdAndDinOrderByDispensedDateDesc(locationId, "00012345"))
                .thenReturn(Optional.of(latestRecord));
        when(userRepository.findByOrganizationIdAndRoleIn(organizationId, List.of(UserRole.owner, UserRole.admin)))
                .thenReturn(List.of(owner));

        service.refreshStaleDrugs();

        ArgumentCaptor<Notification> notification = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notification.capture());
        assertThat(notification.getValue().getOrganizationId()).isEqualTo(organizationId);
        assertThat(notification.getValue().getLocationId()).isEqualTo(locationId);
        assertThat(notification.getValue().getType()).isEqualTo(NotificationType.DRUG_DISCONTINUED);
        assertThat(notification.getValue().getPayload()).contains("00012345");
        assertThat(notification.getValue().getPayload()).contains("MARKETED");
        assertThat(notification.getValue().getPayload()).contains("CANCELLED");
        assertThat(notification.getValue().getPayload()).contains("14");
        verify(drugAlertEmailService).sendDrugDiscontinuedAlert(
                List.of(owner),
                location,
                "00012345",
                "ATORVASTATIN",
                DrugStatus.MARKETED,
                DrugStatus.CANCELLED,
                14
        );
    }

    private void stubCancelledHealthCanadaResponse() {
        when(healthCanadaApiClient.fetchDrugProduct("00012345")).thenReturn(Optional.of(new HealthCanadaDrugProduct(
                "456",
                "00012345",
                "ATORVASTATIN",
                "APOTEX INC",
                "Human"
        )));
        when(healthCanadaApiClient.fetchActiveIngredient("456")).thenReturn(Optional.empty());
        when(healthCanadaApiClient.fetchForm("456")).thenReturn(Optional.empty());
        when(healthCanadaApiClient.fetchSchedule("456")).thenReturn(Optional.empty());
        when(healthCanadaApiClient.fetchStatus("456")).thenReturn(Optional.of(List.of(
                new HealthCanadaProductStatus("Cancelled Post Market", 4)
        )));
        when(healthCanadaApiClient.fetchTherapeuticClass("456")).thenReturn(Optional.empty());
    }

    private Drug drug(String din, String name, DrugStatus status) throws Exception {
        Drug drug = ReflectionUtils.accessibleConstructor(Drug.class).newInstance();
        drug.setDin(din);
        drug.setName(name);
        drug.setStrength("20 MG");
        drug.setForm("TABLET");
        drug.setTherapeuticClass("LIPID MODIFYING AGENTS");
        drug.setManufacturer("APOTEX INC");
        drug.setStatus(status);
        drug.setLastRefreshedAt(Instant.parse("2026-04-01T12:00:00Z"));
        return drug;
    }

    private Location location(UUID id, UUID organizationId) throws Exception {
        Location location = ReflectionUtils.accessibleConstructor(Location.class).newInstance();
        ReflectionTestUtils.setField(location, "id", id);
        ReflectionTestUtils.setField(location, "organizationId", organizationId);
        ReflectionTestUtils.setField(location, "name", "Main Pharmacy");
        ReflectionTestUtils.setField(location, "address", "100 Bank St, Ottawa, ON");
        return location;
    }

    private User user(UUID id, UUID organizationId, String email, UserRole role) throws Exception {
        User user = ReflectionUtils.accessibleConstructor(User.class).newInstance();
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "organizationId", organizationId);
        ReflectionTestUtils.setField(user, "email", email);
        ReflectionTestUtils.setField(user, "role", role);
        return user;
    }

    private DispensingRecord dispensingRecord(UUID locationId, String din, int quantityOnHand) throws Exception {
        DispensingRecord record = ReflectionUtils.accessibleConstructor(DispensingRecord.class).newInstance();
        record.setLocationId(locationId);
        record.setDin(din);
        record.setDispensedDate(LocalDate.parse("2026-04-19"));
        record.setQuantityDispensed(1);
        record.setQuantityOnHand(quantityOnHand);
        return record;
    }
}
