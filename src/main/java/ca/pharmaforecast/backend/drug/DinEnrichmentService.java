package ca.pharmaforecast.backend.drug;

import ca.pharmaforecast.backend.auth.User;
import ca.pharmaforecast.backend.auth.UserRepository;
import ca.pharmaforecast.backend.auth.UserRole;
import ca.pharmaforecast.backend.dispensing.DispensingRecordRepository;
import ca.pharmaforecast.backend.location.Location;
import ca.pharmaforecast.backend.location.LocationRepository;
import ca.pharmaforecast.backend.notification.DrugAlertEmailService;
import ca.pharmaforecast.backend.notification.Notification;
import ca.pharmaforecast.backend.notification.NotificationRepository;
import ca.pharmaforecast.backend.notification.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DinEnrichmentService {

    private static final Duration REFRESH_AGE = Duration.ofDays(7);
    private static final String UNKNOWN = "Unknown";
    private static final Logger LOGGER = LoggerFactory.getLogger(DinEnrichmentService.class);

    private final DrugRepository drugRepository;
    private final HealthCanadaApiClient healthCanadaApiClient;
    private final DinNormalizer dinNormalizer;
    private final Clock clock;
    private final DispensingRecordRepository dispensingRecordRepository;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final DrugAlertEmailService drugAlertEmailService;

    public DinEnrichmentService(
            DrugRepository drugRepository,
            HealthCanadaApiClient healthCanadaApiClient,
            DinNormalizer dinNormalizer,
            Clock clock,
            DispensingRecordRepository dispensingRecordRepository,
            LocationRepository locationRepository,
            UserRepository userRepository,
            NotificationRepository notificationRepository,
            DrugAlertEmailService drugAlertEmailService
    ) {
        this.drugRepository = drugRepository;
        this.healthCanadaApiClient = healthCanadaApiClient;
        this.dinNormalizer = dinNormalizer;
        this.clock = clock;
        this.dispensingRecordRepository = dispensingRecordRepository;
        this.locationRepository = locationRepository;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.drugAlertEmailService = drugAlertEmailService;
    }

    public void enrichSync(List<String> dins) {
        List<String> normalizedDins = dins.stream()
                .map(this::normalizeOrSkip)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (normalizedDins.isEmpty()) {
            return;
        }

        Map<String, Drug> existingByDin = drugRepository.findByDinIn(normalizedDins).stream()
                .collect(Collectors.toMap(Drug::getDin, Function.identity()));
        Instant now = Instant.now(clock);
        Instant staleCutoff = now.minus(REFRESH_AGE);
        List<String> staleDins = normalizedDins.stream()
                .filter(din -> {
                    Drug existing = existingByDin.get(din);
                    return existing == null || !existing.getLastRefreshedAt().isAfter(staleCutoff);
                })
                .toList();

        for (String din : staleDins) {
            enrichSafely(din, existingByDin.get(din), now);
        }
    }

    @Async("dinEnrichmentExecutor")
    public void enrich(List<String> dins) {
        List<String> normalizedDins = dins.stream()
                .map(this::normalizeOrSkip)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (normalizedDins.isEmpty()) {
            return;
        }

        Map<String, Drug> existingByDin = drugRepository.findByDinIn(normalizedDins).stream()
                .collect(Collectors.toMap(Drug::getDin, Function.identity()));
        Instant now = Instant.now(clock);
        Instant staleCutoff = now.minus(REFRESH_AGE);
        List<String> staleDins = normalizedDins.stream()
                .filter(din -> {
                    Drug existing = existingByDin.get(din);
                    return existing == null || !existing.getLastRefreshedAt().isAfter(staleCutoff);
                })
                .toList();

        int enriched = 0;
        int failed = 0;
        if (!staleDins.isEmpty()) {
            try (ExecutorService executorService = Executors.newFixedThreadPool(Math.min(5, staleDins.size()))) {
                List<Boolean> results = staleDins.stream()
                        .map(din -> CompletableFuture.supplyAsync(() -> enrichSafely(din, existingByDin.get(din), now), executorService))
                        .map(CompletableFuture::join)
                        .toList();
                enriched = (int) results.stream().filter(Boolean::booleanValue).count();
                failed = results.size() - enriched;
            }
        }
        LOGGER.info("Enriched {}/{} DINs. {} failed.", enriched, normalizedDins.size(), failed);
    }

    private boolean enrichSafely(String din, Drug existing, Instant now) {
        try {
            enrichOne(din, existing, now);
            return true;
        } catch (RuntimeException ex) {
            LOGGER.warn("DIN enrichment failed for DIN {}", din, ex);
            return false;
        }
    }

    @Scheduled(cron = "${pharmaforecast.jobs.din-refresh-cron}")
    @Transactional
    public void refreshStaleDrugs() {
        Instant now = Instant.now(clock);
        Instant staleCutoff = now.minus(REFRESH_AGE);
        for (Drug drug : drugRepository.findByLastRefreshedAtLessThanEqual(staleCutoff)) {
            DrugStatus previousStatus = drug.getStatus();
            enrichOne(drug.getDin(), drug, now);
            DrugStatus newStatus = drug.getStatus();
            if (previousStatus != newStatus && isDiscontinued(newStatus)) {
                createDiscontinuedAlerts(drug, previousStatus, newStatus);
            }
        }
    }

    private String normalizeOrSkip(String din) {
        try {
            return dinNormalizer.normalize(din);
        } catch (InvalidDinException ex) {
            return null;
        }
    }

    private void enrichOne(String din, Drug existing, Instant now) {
        Optional<HealthCanadaDrugProduct> product = healthCanadaApiClient.fetchDrugProduct(din);
        Drug drug = existing == null ? new Drug() : existing;
        drug.setDin(din);
        drug.setLastRefreshedAt(now);

        if (product.isEmpty()) {
            drug.setName("Unknown Drug");
            drug.setStrength(UNKNOWN);
            drug.setForm(UNKNOWN);
            drug.setTherapeuticClass(UNKNOWN);
            drug.setManufacturer(UNKNOWN);
            drug.setStatus(DrugStatus.UNVERIFIED);
            drugRepository.save(drug);
            return;
        }

        HealthCanadaDrugProduct drugProduct = product.get();
        String drugCode = drugProduct.drugCode();
        List<HealthCanadaActiveIngredient> activeIngredients = healthCanadaApiClient.fetchActiveIngredient(drugCode).orElse(List.of());
        List<HealthCanadaForm> forms = healthCanadaApiClient.fetchForm(drugCode).orElse(List.of());
        List<HealthCanadaSchedule> schedules = healthCanadaApiClient.fetchSchedule(drugCode).orElse(List.of());
        List<HealthCanadaProductStatus> statuses = healthCanadaApiClient.fetchStatus(drugCode).orElse(List.of());
        List<HealthCanadaTherapeuticClass> therapeuticClasses = healthCanadaApiClient.fetchTherapeuticClass(drugCode).orElse(List.of());

        drug.setName(valueOrUnknown(drugProduct.brandName()));
        drug.setManufacturer(valueOrUnknown(drugProduct.companyName()));
        drug.setStrength(strength(activeIngredients));
        drug.setForm(forms.stream()
                .map(HealthCanadaForm::pharmaceuticalFormName)
                .filter(this::hasText)
                .findFirst()
                .orElse(UNKNOWN));
        drug.setTherapeuticClass(therapeuticClass(therapeuticClasses, schedules, activeIngredients));
        drug.setStatus(status(statuses));
        drugRepository.save(drug);
    }

    private boolean isDiscontinued(DrugStatus status) {
        return status == DrugStatus.CANCELLED || status == DrugStatus.DORMANT;
    }

    private void createDiscontinuedAlerts(Drug drug, DrugStatus previousStatus, DrugStatus newStatus) {
        List<Location> locations = locationRepository.findAllById(dispensingRecordRepository.findDistinctLocationIdsByDin(drug.getDin()));
        for (Location location : locations) {
            int currentStockUnits = dispensingRecordRepository
                    .findTopByLocationIdAndDinOrderByDispensedDateDesc(location.getId(), drug.getDin())
                    .map(record -> Optional.ofNullable(record.getQuantityOnHand()).orElse(0))
                    .orElse(0);
            String payload = discontinuedPayload(drug, previousStatus, newStatus, currentStockUnits);
            notificationRepository.save(Notification.create(
                    location.getOrganizationId(),
                    location.getId(),
                    NotificationType.DRUG_DISCONTINUED,
                    payload
            ));
            List<User> recipients = userRepository.findByOrganizationIdAndRoleIn(
                    location.getOrganizationId(),
                    List.of(UserRole.owner, UserRole.admin)
            );
            drugAlertEmailService.sendDrugDiscontinuedAlert(
                    recipients,
                    location,
                    drug.getDin(),
                    drug.getName(),
                    previousStatus,
                    newStatus,
                    currentStockUnits
            );
        }
    }

    private String discontinuedPayload(Drug drug, DrugStatus previousStatus, DrugStatus newStatus, int currentStockUnits) {
        return """
                {"din":"%s","drug_name":"%s","previous_status":"%s","new_status":"%s","current_stock_units":%d}
                """.formatted(
                escapeJson(drug.getDin()),
                escapeJson(drug.getName()),
                previousStatus,
                newStatus,
                currentStockUnits
        ).trim();
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String strength(List<HealthCanadaActiveIngredient> activeIngredients) {
        return activeIngredients.stream()
                .map(ingredient -> join(ingredient.strength(), ingredient.strengthUnit()))
                .filter(this::hasText)
                .findFirst()
                .orElse(UNKNOWN);
    }

    private String therapeuticClass(
            List<HealthCanadaTherapeuticClass> therapeuticClasses,
            List<HealthCanadaSchedule> schedules,
            List<HealthCanadaActiveIngredient> activeIngredients
    ) {
        Optional<String> atc = therapeuticClasses.stream()
                .map(HealthCanadaTherapeuticClass::atc)
                .filter(this::hasText)
                .findFirst();
        if (atc.isPresent()) {
            return atc.get();
        }
        boolean otc = schedules.stream()
                .map(HealthCanadaSchedule::scheduleName)
                .filter(Objects::nonNull)
                .anyMatch(schedule -> schedule.toLowerCase().contains("non-prescription"));
        if (otc) {
            return "Over-the-Counter";
        }
        return activeIngredients.stream()
                .map(HealthCanadaActiveIngredient::ingredientName)
                .filter(this::hasText)
                .findFirst()
                .orElse(UNKNOWN);
    }

    private DrugStatus status(List<HealthCanadaProductStatus> statuses) {
        return statuses.stream()
                .max(Comparator.comparing(status -> Optional.ofNullable(status.externalStatusCode()).orElse(0)))
                .map(this::mapStatus)
                .orElse(DrugStatus.UNKNOWN);
    }

    private DrugStatus mapStatus(HealthCanadaProductStatus status) {
        Integer code = status.externalStatusCode();
        if (code != null) {
            return switch (code) {
                case 1 -> DrugStatus.APPROVED;
                case 2 -> DrugStatus.MARKETED;
                case 3, 4, 9, 10, 12, 14, 15 -> DrugStatus.CANCELLED;
                case 6 -> DrugStatus.DORMANT;
                default -> DrugStatus.ACTIVE;
            };
        }
        String value = status.status() == null ? "" : status.status().toLowerCase();
        if (value.contains("cancel")) {
            return DrugStatus.CANCELLED;
        }
        if (value.contains("dormant")) {
            return DrugStatus.DORMANT;
        }
        if (value.contains("marketed")) {
            return DrugStatus.MARKETED;
        }
        if (value.contains("approved")) {
            return DrugStatus.APPROVED;
        }
        return DrugStatus.UNKNOWN;
    }

    private String join(String value, String unit) {
        if (!hasText(value) && !hasText(unit)) {
            return UNKNOWN;
        }
        if (!hasText(value)) {
            return unit.trim();
        }
        if (!hasText(unit)) {
            return value.trim();
        }
        return value.trim() + " " + unit.trim();
    }

    private String valueOrUnknown(String value) {
        return hasText(value) ? value.trim() : UNKNOWN;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
