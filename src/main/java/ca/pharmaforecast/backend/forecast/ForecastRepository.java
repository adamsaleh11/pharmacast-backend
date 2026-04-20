package ca.pharmaforecast.backend.forecast;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ForecastRepository extends JpaRepository<Forecast, UUID> {
    Optional<Forecast> findTopByLocationIdAndDinOrderByGeneratedAtDesc(UUID locationId, String din);

    List<Forecast> findByLocationIdOrderByGeneratedAtDesc(UUID locationId);

    List<Forecast> findByLocationIdAndDinIn(UUID locationId, List<String> dins);
}
