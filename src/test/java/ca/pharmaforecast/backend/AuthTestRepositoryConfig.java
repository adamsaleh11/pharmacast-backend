package ca.pharmaforecast.backend;

import ca.pharmaforecast.backend.auth.User;
import ca.pharmaforecast.backend.auth.AuthBootstrapService;
import ca.pharmaforecast.backend.auth.UserRepository;
import ca.pharmaforecast.backend.dispensing.DispensingRecordImportRepository;
import ca.pharmaforecast.backend.dispensing.DispensingRecordRepository;
import ca.pharmaforecast.backend.drug.Drug;
import ca.pharmaforecast.backend.drug.DrugRepository;
import ca.pharmaforecast.backend.location.Location;
import ca.pharmaforecast.backend.location.LocationRepository;
import ca.pharmaforecast.backend.notification.DrugAlertEmailService;
import ca.pharmaforecast.backend.notification.NotificationRepository;
import ca.pharmaforecast.backend.upload.CsvUpload;
import ca.pharmaforecast.backend.upload.CsvProcessingJob;
import ca.pharmaforecast.backend.upload.CsvUploadRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;

@TestConfiguration
class AuthTestRepositoryConfig {

    static final Map<UserKey, User> USERS = new HashMap<>();
    static final Map<UUID, List<Location>> LOCATIONS = new HashMap<>();
    static final Map<UUID, CsvUpload> CSV_UPLOADS = new HashMap<>();
    static final Map<String, Drug> DRUGS = new HashMap<>();
    static AuthBootstrapService.BootstrapCommand LAST_BOOTSTRAP_COMMAND;
    static AuthBootstrapService.BootstrapResult BOOTSTRAP_RESULT;
    static CapturedCsvJob LAST_CSV_JOB;

    static void reset() {
        USERS.clear();
        LOCATIONS.clear();
        CSV_UPLOADS.clear();
        DRUGS.clear();
        LAST_BOOTSTRAP_COMMAND = null;
        BOOTSTRAP_RESULT = null;
        LAST_CSV_JOB = null;
    }

    @Bean
    UserRepository userRepository() {
        return (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(),
                new Class<?>[]{UserRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndEmail" -> Optional.ofNullable(USERS.get(new UserKey((UUID) args[0], (String) args[1])));
                    case "toString" -> "AuthTestUserRepository";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    @Bean
    LocationRepository locationRepository() {
        return (LocationRepository) Proxy.newProxyInstance(
                LocationRepository.class.getClassLoader(),
                new Class<?>[]{LocationRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByOrganizationIdAndDeactivatedAtIsNullOrderByNameAsc" ->
                            LOCATIONS.getOrDefault((UUID) args[0], List.of());
                    case "findById" -> LOCATIONS.values().stream()
                            .flatMap(List::stream)
                            .filter(location -> location.getId().equals(args[0]))
                            .findFirst();
                    case "toString" -> "AuthTestLocationRepository";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    @Bean
    CsvUploadRepository csvUploadRepository() {
        return (CsvUploadRepository) Proxy.newProxyInstance(
                CsvUploadRepository.class.getClassLoader(),
                new Class<?>[]{CsvUploadRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "save" -> saveUpload((CsvUpload) args[0]);
                    case "findById" -> Optional.ofNullable(CSV_UPLOADS.get((UUID) args[0]));
                    case "findTop10ByLocationIdOrderByUploadedAtDesc" -> CSV_UPLOADS.values().stream()
                            .filter(upload -> upload.getLocationId().equals(args[0]))
                            .sorted(Comparator.comparing(CsvUpload::getUploadedAt).reversed())
                            .limit(10)
                            .toList();
                    case "findByIdAndLocationId" -> Optional.ofNullable(CSV_UPLOADS.get((UUID) args[0]))
                            .filter(upload -> upload.getLocationId().equals(args[1]));
                    case "findAll" -> List.copyOf(CSV_UPLOADS.values());
                    case "toString" -> "AuthTestCsvUploadRepository";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> {
                        if (method.getName().equals("findAll") && args != null && args.length == 1 && args[0] instanceof Sort) {
                            yield List.copyOf(CSV_UPLOADS.values());
                        }
                        throw new UnsupportedOperationException(method.getName());
                    }
                }
        );
    }

    @Bean
    DrugRepository drugRepository() {
        return (DrugRepository) Proxy.newProxyInstance(
                DrugRepository.class.getClassLoader(),
                new Class<?>[]{DrugRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByDin" -> Optional.ofNullable(DRUGS.get((String) args[0]));
                    case "toString" -> "AuthTestDrugRepository";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    @Bean
    AuthBootstrapService authBootstrapService() {
        return command -> {
            LAST_BOOTSTRAP_COMMAND = command;
            if (BOOTSTRAP_RESULT == null) {
                throw new IllegalStateException("Bootstrap result was not configured");
            }
            return BOOTSTRAP_RESULT;
        };
    }

    @Bean
    DispensingRecordImportRepository dispensingRecordImportRepository() {
        return mock(DispensingRecordImportRepository.class);
    }

    @Bean
    DispensingRecordRepository dispensingRecordRepository() {
        return mock(DispensingRecordRepository.class);
    }

    @Bean
    NotificationRepository notificationRepository() {
        return mock(NotificationRepository.class);
    }

    @Bean
    DrugAlertEmailService drugAlertEmailService() {
        return mock(DrugAlertEmailService.class);
    }

    @Bean
    @Primary
    CsvProcessingJob csvProcessingJob() {
        return (uploadId, locationId, csvBytes) -> LAST_CSV_JOB = new CapturedCsvJob(uploadId, locationId, csvBytes);
    }

    static void putUser(UUID id, String email, User user) {
        USERS.put(new UserKey(id, email), user);
    }

    static void putLocations(UUID organizationId, List<Location> locations) {
        LOCATIONS.put(organizationId, new ArrayList<>(locations));
    }

    static CsvUpload upload(UUID uploadId) {
        return CSV_UPLOADS.get(uploadId);
    }

    static void putUpload(CsvUpload upload) {
        CSV_UPLOADS.put(upload.getId(), upload);
    }

    static void putDrug(Drug drug) {
        DRUGS.put(drug.getDin(), drug);
    }

    static int uploadCount() {
        return CSV_UPLOADS.size();
    }

    static void bootstrapReturns(AuthBootstrapService.BootstrapResult result) {
        BOOTSTRAP_RESULT = result;
    }

    private CsvUpload saveUpload(CsvUpload upload) {
        if (upload.getId() == null) {
            ReflectionTestUtils.setField(upload, "id", UUID.randomUUID());
        }
        CSV_UPLOADS.put(upload.getId(), upload);
        return upload;
    }

    private record UserKey(UUID id, String email) {
    }

    record CapturedCsvJob(UUID uploadId, UUID locationId, byte[] csvBytes) {
    }
}
