package ca.pharmaforecast.backend.forecast;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ForecastRepository extends JpaRepository<Forecast, UUID> {
    Optional<Forecast> findTopByLocationIdAndDinOrderByGeneratedAtDesc(UUID locationId, String din);

    List<Forecast> findByLocationIdOrderByGeneratedAtDesc(UUID locationId);

    List<Forecast> findByLocationIdAndDinIn(UUID locationId, List<String> dins);

    List<Forecast> findByLocationIdAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(UUID locationId, Instant generatedAt);

    List<Forecast> findByLocationIdAndGeneratedAtBetweenOrderByGeneratedAtAsc(UUID locationId, Instant start, Instant end);

    Optional<Forecast> findByLocationIdAndDinAndForecastHorizonDays(UUID locationId, String din, Integer horizonDays);

    List<Forecast> findByLocationIdAndDin(UUID locationId, String din);

    @Modifying
    @Query("UPDATE Forecast f SET f.isOutdated = true WHERE f.locationId = :locationId AND f.din = :din")
    void markAsOutdatedByLocationAndDin(@Param("locationId") UUID locationId, @Param("din") String din);
}
