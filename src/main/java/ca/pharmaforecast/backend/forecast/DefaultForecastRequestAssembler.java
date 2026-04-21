package ca.pharmaforecast.backend.forecast;

import ca.pharmaforecast.backend.dispensing.DispensingRecord;
import ca.pharmaforecast.backend.dispensing.DispensingRecordRepository;
import ca.pharmaforecast.backend.common.exception.StockNotSetException;
import ca.pharmaforecast.backend.currentstock.CurrentStockRepository;
import ca.pharmaforecast.backend.location.Location;
import ca.pharmaforecast.backend.location.LocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class DefaultForecastRequestAssembler implements ForecastRequestAssembler {

    private final LocationRepository locationRepository;
    private final DispensingRecordRepository dispensingRecordRepository;
    private final CurrentStockRepository currentStockRepository;
    private final DrugThresholdRepository drugThresholdRepository;

    public DefaultForecastRequestAssembler(
            LocationRepository locationRepository,
            DispensingRecordRepository dispensingRecordRepository,
            CurrentStockRepository currentStockRepository,
            DrugThresholdRepository drugThresholdRepository
    ) {
        this.locationRepository = locationRepository;
        this.dispensingRecordRepository = dispensingRecordRepository;
        this.currentStockRepository = currentStockRepository;
        this.drugThresholdRepository = drugThresholdRepository;
    }

    @Override
    public ForecastRequest build(UUID locationId, String din, Integer horizonDays) {
        int quantityOnHand = currentStockRepository.findByLocationIdAndDin(locationId, din)
                .map(currentStock -> currentStock.getQuantity() == null ? 0 : currentStock.getQuantity())
                .orElseThrow(() -> new StockNotSetException(din));
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
                threshold.redThresholdDays(),
                threshold.amberThresholdDays(),
                supplementalHistory
        );
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

    private LocalDate mondayOf(LocalDate date) {
        return date.with(java.time.DayOfWeek.MONDAY);
    }

    record ForecastThreshold(Integer leadTimeDays, SafetyMultiplier safetyMultiplier, Integer redThresholdDays, Integer amberThresholdDays) {
        static ForecastThreshold defaults() {
            return new ForecastThreshold(2, SafetyMultiplier.balanced, 3, 7);
        }

        static ForecastThreshold from(DrugThreshold threshold) {
            return new ForecastThreshold(
                    threshold.getLeadTimeDays(),
                    threshold.getSafetyMultiplier(),
                    threshold.getRedThresholdDays(),
                    threshold.getAmberThresholdDays()
            );
        }
    }
}
