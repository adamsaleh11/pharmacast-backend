package ca.pharmaforecast.backend.forecast;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DrugThresholdRepository extends JpaRepository<DrugThreshold, UUID> {
    Optional<DrugThreshold> findByLocationIdAndDin(UUID locationId, String din);

    List<DrugThreshold> findByLocationIdAndDinIn(UUID locationId, List<String> dins);
}
