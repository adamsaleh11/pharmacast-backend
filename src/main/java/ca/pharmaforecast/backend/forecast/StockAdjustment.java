package ca.pharmaforecast.backend.forecast;

import ca.pharmaforecast.backend.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

import static lombok.AccessLevel.PROTECTED;

@Getter
@Setter
@Entity
@Table(name = "stock_adjustments")
@NoArgsConstructor(access = PROTECTED)
public class StockAdjustment extends BaseEntity {

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "din", nullable = false)
    private String din;

    @Column(name = "adjustment_quantity", nullable = false)
    private Integer adjustmentQuantity;

    @Column(name = "adjusted_at", nullable = false)
    private Instant adjustedAt;

    @Column(name = "note", nullable = false)
    private String note;

    public UUID getLocationId() {
        return locationId;
    }

    public String getDin() {
        return din;
    }

    public Integer getAdjustmentQuantity() {
        return adjustmentQuantity;
    }

    public Instant getAdjustedAt() {
        return adjustedAt;
    }
}
