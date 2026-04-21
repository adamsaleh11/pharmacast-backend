package ca.pharmaforecast.backend.forecast;

import ca.pharmaforecast.backend.currentstock.CurrentStock;
import ca.pharmaforecast.backend.currentstock.CurrentStockRepository;
import ca.pharmaforecast.backend.dispensing.DispensingRecordRepository;
import ca.pharmaforecast.backend.location.Location;
import ca.pharmaforecast.backend.location.LocationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.ReflectionUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultForecastRequestAssemblerTest {

    @Test
    void buildUsesCurrentStockAndExplicitThresholdDefaultsInsteadOfHistoricalDispenseRows() throws Exception {
        UUID locationId = UUID.randomUUID();
        String din = "00012345";

        LocationRepository locationRepository = mock(LocationRepository.class);
        DispensingRecordRepository dispensingRecordRepository = mock(DispensingRecordRepository.class);
        CurrentStockRepository currentStockRepository = mock(CurrentStockRepository.class);
        DrugThresholdRepository drugThresholdRepository = mock(DrugThresholdRepository.class);

        Location location = ReflectionUtils.accessibleConstructor(Location.class).newInstance();
        ReflectionTestUtils.setField(location, "id", locationId);
        ReflectionTestUtils.setField(location, "organizationId", UUID.randomUUID());
        when(locationRepository.findById(locationId)).thenReturn(Optional.of(location));
        when(locationRepository.findByOrganizationIdAndDeactivatedAtIsNullOrderByNameAsc(any(UUID.class))).thenReturn(List.of(location));

        CurrentStock currentStock = ReflectionUtils.accessibleConstructor(CurrentStock.class).newInstance();
        currentStock.setLocationId(locationId);
        currentStock.setDin(din);
        currentStock.setQuantity(42);
        when(currentStockRepository.findByLocationIdAndDin(locationId, din)).thenReturn(Optional.of(currentStock));

        when(dispensingRecordRepository.findByDin(din)).thenReturn(List.of());
        when(drugThresholdRepository.findByLocationIdAndDin(locationId, din)).thenReturn(Optional.of(threshold(locationId, din, 5, 1.5, 4, 9)));

        DefaultForecastRequestAssembler assembler = new DefaultForecastRequestAssembler(
                locationRepository,
                dispensingRecordRepository,
                currentStockRepository,
                drugThresholdRepository
        );

        ForecastRequest request = assembler.build(locationId, din, 14);

        assertThat(request.locationId()).isEqualTo(locationId.toString());
        assertThat(request.din()).isEqualTo(din);
        assertThat(request.horizonDays()).isEqualTo(14);
        assertThat(request.quantityOnHand()).isEqualTo(42);
        assertThat(request.leadTimeDays()).isEqualTo(5);
        assertThat(request.safetyMultiplier()).isEqualTo(1.5);
        assertThat(request.redThresholdDays()).isEqualTo(4);
        assertThat(request.amberThresholdDays()).isEqualTo(9);
    }

    private static DrugThreshold threshold(UUID locationId, String din, int leadTimeDays, double safetyMultiplier, int redThresholdDays, int amberThresholdDays) throws Exception {
        DrugThreshold threshold = ReflectionUtils.accessibleConstructor(DrugThreshold.class).newInstance();
        ReflectionTestUtils.setField(threshold, "locationId", locationId);
        ReflectionTestUtils.setField(threshold, "din", din);
        ReflectionTestUtils.setField(threshold, "leadTimeDays", leadTimeDays);
        ReflectionTestUtils.setField(threshold, "redThresholdDays", redThresholdDays);
        ReflectionTestUtils.setField(threshold, "amberThresholdDays", amberThresholdDays);
        ReflectionTestUtils.setField(threshold, "safetyMultiplier", safetyMultiplier == 1.5
                ? SafetyMultiplier.conservative
                : safetyMultiplier == 1.0
                ? SafetyMultiplier.balanced
                : SafetyMultiplier.aggressive);
        ReflectionTestUtils.setField(threshold, "notificationsEnabled", true);
        return threshold;
    }
}
