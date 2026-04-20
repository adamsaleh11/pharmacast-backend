package ca.pharmaforecast.backend.forecast;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, UUID> {
    List<StockAdjustment> findByLocationIdAndDin(UUID locationId, String din);

    List<StockAdjustment> findByLocationIdAndDinAndAdjustedAtAfter(UUID locationId, String din, java.time.Instant adjustedAt);
}
