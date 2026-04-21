package ca.pharmaforecast.backend.drug;

import ca.pharmaforecast.backend.currentstock.CurrentStock;
import ca.pharmaforecast.backend.currentstock.CurrentStockRepository;
import ca.pharmaforecast.backend.dispensing.DispensingRecord;
import ca.pharmaforecast.backend.dispensing.DispensingRecordRepository;
import ca.pharmaforecast.backend.forecast.DrugThreshold;
import ca.pharmaforecast.backend.forecast.DrugThresholdRepository;
import ca.pharmaforecast.backend.forecast.Forecast;
import ca.pharmaforecast.backend.forecast.ForecastRepository;
import ca.pharmaforecast.backend.forecast.ReorderStatus;
import ca.pharmaforecast.backend.forecast.SafetyMultiplier;
import ca.pharmaforecast.backend.forecast.StockAdjustment;
import ca.pharmaforecast.backend.forecast.StockAdjustmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class LocationDrugDetailService {

    private static final DrugDetailResponse.DrugThresholdDto SYSTEM_DEFAULT_THRESHOLD =
            new DrugDetailResponse.DrugThresholdDto(2, 3, 7, "BALANCED", true);

    private final DrugRepository drugRepository;
    private final CurrentStockRepository currentStockRepository;
    private final ForecastRepository forecastRepository;
    private final DrugThresholdRepository drugThresholdRepository;
    private final DispensingRecordRepository dispensingRecordRepository;
    private final StockAdjustmentRepository stockAdjustmentRepository;
    private final Clock clock;

    public LocationDrugDetailService(
            DrugRepository drugRepository,
            CurrentStockRepository currentStockRepository,
            ForecastRepository forecastRepository,
            DrugThresholdRepository drugThresholdRepository,
            DispensingRecordRepository dispensingRecordRepository,
            StockAdjustmentRepository stockAdjustmentRepository,
            Clock clock
    ) {
        this.drugRepository = drugRepository;
        this.currentStockRepository = currentStockRepository;
        this.forecastRepository = forecastRepository;
        this.drugThresholdRepository = drugThresholdRepository;
        this.dispensingRecordRepository = dispensingRecordRepository;
        this.stockAdjustmentRepository = stockAdjustmentRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DrugDetailResponse getDetail(UUID locationId, String din) {
        Drug drug = drugRepository.findByDin(din).orElse(null);

        CurrentStock currentStock = currentStockRepository.findByLocationIdAndDin(locationId, din).orElse(null);
        Forecast forecast = forecastRepository.findTopByLocationIdAndDinOrderByGeneratedAtDesc(locationId, din).orElse(null);
        DrugThreshold threshold = drugThresholdRepository.findByLocationIdAndDin(locationId, din).orElse(null);

        return new DrugDetailResponse(
                toDrugDto(din, drug),
                currentStock == null ? null : currentStock.getQuantity(),
                currentStock == null ? null : currentStock.getUpdatedAt().toInstant(),
                forecast == null ? null : toForecastDto(forecast),
                threshold == null ? null : toThresholdDto(threshold),
                loadDispensingHistory(locationId, din),
                loadStockAdjustments(locationId, din)
        );
    }

    public DrugDetailResponse.DrugThresholdDto upsertThreshold(UUID locationId, String din, DrugThresholdUpsertRequest request) {
        requireDrug(din);
        DrugThreshold threshold = drugThresholdRepository.findByLocationIdAndDin(locationId, din)
                .orElseGet(() -> instantiate(DrugThreshold.class));
        threshold.setLocationId(locationId);
        threshold.setDin(din);
        threshold.setLeadTimeDays(request.leadTimeDays() != null
                ? request.leadTimeDays()
                : threshold.getLeadTimeDays() != null ? threshold.getLeadTimeDays() : SYSTEM_DEFAULT_THRESHOLD.leadTimeDays());
        threshold.setRedThresholdDays(request.redThresholdDays() != null
                ? request.redThresholdDays()
                : threshold.getRedThresholdDays() != null ? threshold.getRedThresholdDays() : SYSTEM_DEFAULT_THRESHOLD.redThresholdDays());
        threshold.setAmberThresholdDays(request.amberThresholdDays() != null
                ? request.amberThresholdDays()
                : threshold.getAmberThresholdDays() != null ? threshold.getAmberThresholdDays() : SYSTEM_DEFAULT_THRESHOLD.amberThresholdDays());
        threshold.setSafetyMultiplier(request.safetyMultiplier() != null
                ? safetyMultiplier(request.safetyMultiplier())
                : threshold.getSafetyMultiplier() != null ? threshold.getSafetyMultiplier() : SafetyMultiplier.balanced);
        threshold.setNotificationsEnabled(request.notificationsEnabled() != null
                ? request.notificationsEnabled()
                : threshold.getNotificationsEnabled() != null ? threshold.getNotificationsEnabled() : SYSTEM_DEFAULT_THRESHOLD.notificationsEnabled());
        return toThresholdDto(drugThresholdRepository.save(threshold));
    }

    public void resetThreshold(UUID locationId, String din) {
        requireDrug(din);
        drugThresholdRepository.findByLocationIdAndDin(locationId, din)
                .ifPresent(drugThresholdRepository::delete);
    }

    public DrugDetailResponse.StockAdjustmentDto adjustStock(UUID locationId, String din, StockAdjustmentCreateRequest request) {
        requireDrug(din);
        StockAdjustment adjustment = instantiate(StockAdjustment.class);
        adjustment.setLocationId(locationId);
        adjustment.setDin(din);
        adjustment.setAdjustmentQuantity(request.adjustmentQuantity());
        adjustment.setAdjustedAt(Instant.now(clock));
        adjustment.setNote(request.note().trim());
        StockAdjustment saved = stockAdjustmentRepository.save(adjustment);
        return new DrugDetailResponse.StockAdjustmentDto(saved.getAdjustedAt(), saved.getAdjustmentQuantity(), saved.getNote());
    }

    private DrugDetailResponse.ForecastDto toForecastDto(Forecast forecast) {
        return new DrugDetailResponse.ForecastDto(
                forecast.getDin(),
                forecast.getGeneratedAt(),
                forecast.getForecastHorizonDays(),
                forecast.getPredictedQuantity(),
                forecast.getConfidence().name().toUpperCase(),
                forecast.getDaysOfSupply() == null ? null : forecast.getDaysOfSupply().doubleValue(),
                forecast.getReorderStatus().name().toUpperCase(),
                forecast.getModelPath(),
                forecast.getProphetLower() == null ? null : forecast.getProphetLower().doubleValue(),
                forecast.getProphetUpper() == null ? null : forecast.getProphetUpper().doubleValue(),
                forecast.getAvgDailyDemand() == null ? null : forecast.getAvgDailyDemand().doubleValue(),
                forecast.getReorderPoint() == null ? null : forecast.getReorderPoint().doubleValue(),
                forecast.getDataPointsUsed()
        );
    }

    private DrugDetailResponse.DrugThresholdDto toThresholdDto(DrugThreshold threshold) {
        return new DrugDetailResponse.DrugThresholdDto(
                threshold.getLeadTimeDays(),
                threshold.getRedThresholdDays(),
                threshold.getAmberThresholdDays(),
                threshold.getSafetyMultiplier().name().toUpperCase(),
                threshold.getNotificationsEnabled()
        );
    }

    private List<DrugDetailResponse.DispensingHistoryDto> loadDispensingHistory(UUID locationId, String din) {
        LocalDate cutoff = LocalDate.now(clock).minusWeeks(52);
        return dispensingRecordRepository.findByLocationIdAndDin(locationId, din).stream()
                .filter(record -> !record.getDispensedDate().isBefore(cutoff))
                .collect(Collectors.groupingBy(
                        record -> record.getDispensedDate().with(java.time.DayOfWeek.MONDAY),
                        Collectors.summingInt(DispensingRecord::getQuantityDispensed)
                ))
                .entrySet().stream()
                .sorted(Map.Entry.<LocalDate, Integer>comparingByKey().reversed())
                .limit(52)
                .map(entry -> new DrugDetailResponse.DispensingHistoryDto(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<DrugDetailResponse.StockAdjustmentDto> loadStockAdjustments(UUID locationId, String din) {
        return stockAdjustmentRepository.findByLocationIdAndDin(locationId, din).stream()
                .sorted(Comparator.comparing(StockAdjustment::getAdjustedAt).reversed())
                .limit(10)
                .map(adjustment -> new DrugDetailResponse.StockAdjustmentDto(
                        adjustment.getAdjustedAt(),
                        adjustment.getAdjustmentQuantity(),
                        adjustment.getNote()
                ))
                .toList();
    }

    private SafetyMultiplier safetyMultiplier(String value) {
        return SafetyMultiplier.valueOf(value.toLowerCase());
    }

    private <T> T instantiate(Class<T> type) {
        try {
            return BeanUtils.instantiateClass(ReflectionUtils.accessibleConstructor(type));
        } catch (NoSuchMethodException ex) {
            throw new IllegalStateException("Missing no-arg constructor for %s".formatted(type.getName()), ex);
        }
    }

    private Drug requireDrug(String din) {
        return drugRepository.findByDin(din)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Drug not found"));
    }

    private DrugDetailResponse.DrugDto toDrugDto(String din, Drug drug) {
        if (drug == null) {
            return new DrugDetailResponse.DrugDto(
                    din,
                    "UNKNOWN",
                    "UNKNOWN",
                    "UNKNOWN",
                    "UNKNOWN",
                    "UNKNOWN",
                    DrugStatus.UNKNOWN.name()
            );
        }
        return new DrugDetailResponse.DrugDto(
                drug.getDin(),
                drug.getName(),
                drug.getStrength(),
                drug.getForm(),
                drug.getTherapeuticClass(),
                drug.getManufacturer(),
                drug.getStatus().name()
        );
    }
}
