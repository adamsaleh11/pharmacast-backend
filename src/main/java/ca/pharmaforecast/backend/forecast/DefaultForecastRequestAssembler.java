package ca.pharmaforecast.backend.forecast;

import ca.pharmaforecast.backend.dispensing.DispensingRecord;
import ca.pharmaforecast.backend.dispensing.DispensingRecordRepository;
import ca.pharmaforecast.backend.location.Location;
import ca.pharmaforecast.backend.location.LocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class DefaultForecastRequestAssembler implements ForecastRequestAssembler {

    private final LocationRepository locationRepository;
    private final DispensingRecordRepository dispensingRecordRepository;
    private final StockAdjustmentRepository stockAdjustmentRepository;
    private final DrugThresholdRepository drugThresholdRepository;

    public DefaultForecastRequestAssembler(
            LocationRepository locationRepository,
            DispensingRecordRepository dispensingRecordRepository,
            StockAdjustmentRepository stockAdjustmentRepository,
            DrugThresholdRepository drugThresholdRepository
    ) {
        this.locationRepository = locationRepository;
        this.dispensingRecordRepository = dispensingRecordRepository;
        this.stockAdjustmentRepository = stockAdjustmentRepository;
        this.drugThresholdRepository = drugThresholdRepository;
    }

    @Override
    public ForecastRequest build(UUID locationId, String din, Integer horizonDays) {
        int quantityOnHand = getEffectiveStock(locationId, din);
        ForecastThreshold threshold = drugThresholdRepository.findByLocationIdAndDin(locationId, din)
                .map(ForecastThreshold::from)
                .orElseGet(ForecastThreshold::defaults);
        List<WeeklyQuantityDto> supplementalHistory = getNetworkSupplementalHistory(locationId, din);
        return new ForecastRequest(
                locationId.toString(),
                din,
                horizonDays,
                quantityOnHand,
                threshold.leadTimeDays(),
                threshold.safetyMultiplier().value(),
                supplementalHistory
        );
    }

    int getEffectiveStock(UUID locationId, String din) {
        return dispensingRecordRepository.findTopByLocationIdAndDinOrderByDispensedDateDesc(locationId, din)
                .map(latest -> latest.getQuantityOnHand() + laterStockAdjustments(locationId, din, latest.getDispensedDate()))
                .orElse(0);
    }

    List<WeeklyQuantityDto> getNetworkSupplementalHistory(UUID locationId, String din) {
        Location location = locationRepository.findById(locationId).orElse(null);
        if (location == null) {
            return List.of();
        }
        List<UUID> orgLocationIds = locationRepository.findByOrganizationIdAndDeactivatedAtIsNullOrderByNameAsc(location.getOrganizationId())
                .stream()
                .map(Location::getId)
                .toList();
        return dispensingRecordRepository.findByDin(din).stream()
                .filter(record -> orgLocationIds.contains(record.getLocationId()))
                .filter(record -> !locationId.equals(record.getLocationId()))
                .collect(java.util.stream.Collectors.groupingBy(
                        record -> mondayOf(record.getDispensedDate()),
                        java.util.stream.Collectors.summingInt(DispensingRecord::getQuantityDispensed)
                ))
                .entrySet()
                .stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(entry -> new WeeklyQuantityDto(entry.getKey().toString(), entry.getValue()))
                .toList();
    }

    private int laterStockAdjustments(UUID locationId, String din, LocalDate cutoff) {
        return stockAdjustmentRepository.findByLocationIdAndDinAndAdjustedAtAfter(locationId, din, cutoff.atStartOfDay().toInstant(ZoneOffset.UTC))
                .stream()
                .mapToInt(StockAdjustment::getAdjustmentQuantity)
                .sum();
    }

    private LocalDate mondayOf(LocalDate date) {
        return date.with(java.time.DayOfWeek.MONDAY);
    }

    record ForecastThreshold(Integer leadTimeDays, SafetyMultiplier safetyMultiplier) {
        static ForecastThreshold defaults() {
            return new ForecastThreshold(2, SafetyMultiplier.balanced);
        }

        static ForecastThreshold from(DrugThreshold threshold) {
            return new ForecastThreshold(threshold.getLeadTimeDays(), threshold.getSafetyMultiplier());
        }
    }
}
