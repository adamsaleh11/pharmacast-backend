package ca.pharmaforecast.backend.currentstock;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public interface CurrentStockRepository extends JpaRepository<CurrentStock, UUID> {
    java.util.Optional<CurrentStock> findByLocationIdAndDin(UUID locationId, String din);

    List<CurrentStock> findAllByLocationId(UUID locationId);

    default Map<String, Integer> getStockMapForLocation(UUID locationId) {
        return findAllByLocationId(locationId).stream()
                .collect(Collectors.toMap(CurrentStock::getDin, CurrentStock::getQuantity));
    }
}
