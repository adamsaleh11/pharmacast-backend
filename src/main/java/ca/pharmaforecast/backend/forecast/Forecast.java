package ca.pharmaforecast.backend.forecast;

import ca.pharmaforecast.backend.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static lombok.AccessLevel.PROTECTED;

@Getter
@Setter
@Entity
@Table(name = "forecasts")
@NoArgsConstructor(access = PROTECTED)
public class Forecast extends BaseEntity {

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "din", nullable = false)
    private String din;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "forecast_horizon_days", nullable = false)
    private Integer forecastHorizonDays;

    @Column(name = "predicted_quantity", nullable = false)
    private Integer predictedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence", nullable = false)
    private ForecastConfidence confidence;

    @Column(name = "days_of_supply", nullable = false, precision = 12, scale = 1)
    private BigDecimal daysOfSupply;

    @Enumerated(EnumType.STRING)
    @Column(name = "reorder_status", nullable = false)
    private ReorderStatus reorderStatus;

    @Column(name = "prophet_lower", nullable = false, precision = 12, scale = 2)
    private BigDecimal prophetLower;

    @Column(name = "prophet_upper", nullable = false, precision = 12, scale = 2)
    private BigDecimal prophetUpper;

    @Column(name = "avg_daily_demand", precision = 12, scale = 2)
    private BigDecimal avgDailyDemand;

    @Column(name = "reorder_point", precision = 12, scale = 2)
    private BigDecimal reorderPoint;

    @Column(name = "data_points_used")
    private Integer dataPointsUsed;
}
