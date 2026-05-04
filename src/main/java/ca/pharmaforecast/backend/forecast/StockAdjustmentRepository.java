package ca.pharmaforecast.backend.forecast;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, UUID> {
    List<StockAdjustment> findByLocationIdAndDin(UUID locationId, String din);

    List<StockAdjustment> findByLocationIdAndDinAndAdjustedAtAfter(UUID locationId, String din, Instant adjustedAt);

    List<StockAdjustment> findByLocationIdAndAdjustedAtBetween(UUID locationId, Instant start, Instant end);
}
