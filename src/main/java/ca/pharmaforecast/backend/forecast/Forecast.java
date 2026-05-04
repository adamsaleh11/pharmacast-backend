package ca.pharmaforecast.backend.forecast;

import ca.pharmaforecast.backend.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Convert;
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

    @Convert(converter = ForecastConfidenceConverter.class)
    @Column(name = "confidence", nullable = false)
    private ForecastConfidence confidence;

    @Column(name = "days_of_supply", nullable = false, precision = 12, scale = 1)
    private BigDecimal daysOfSupply;

    @Convert(converter = ReorderStatusConverter.class)
    @Column(name = "reorder_status", nullable = false)
    private ReorderStatus reorderStatus;

    @Column(name = "model_path", nullable = false)
    private String modelPath;

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

    @Column(name = "is_outdated", nullable = false)
    private Boolean isOutdated = false;

    public void setLocationId(UUID locationId) {
        this.locationId = locationId;
    }

    public void setDin(String din) {
        this.din = din;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    public void setForecastHorizonDays(Integer forecastHorizonDays) {
        this.forecastHorizonDays = forecastHorizonDays;
    }

    public void setPredictedQuantity(Integer predictedQuantity) {
        this.predictedQuantity = predictedQuantity;
    }

    public void setConfidence(ForecastConfidence confidence) {
        this.confidence = confidence;
    }

    public void setDaysOfSupply(BigDecimal daysOfSupply) {
        this.daysOfSupply = daysOfSupply;
    }

    public void setReorderStatus(ReorderStatus reorderStatus) {
        this.reorderStatus = reorderStatus;
    }

    public void setModelPath(String modelPath) {
        this.modelPath = modelPath;
    }

    public void setProphetLower(BigDecimal prophetLower) {
        this.prophetLower = prophetLower;
    }

    public void setProphetUpper(BigDecimal prophetUpper) {
        this.prophetUpper = prophetUpper;
    }

    public void setAvgDailyDemand(BigDecimal avgDailyDemand) {
        this.avgDailyDemand = avgDailyDemand;
    }

    public void setReorderPoint(BigDecimal reorderPoint) {
        this.reorderPoint = reorderPoint;
    }

    public void setDataPointsUsed(Integer dataPointsUsed) {
        this.dataPointsUsed = dataPointsUsed;
    }

    public UUID getLocationId() {
        return locationId;
    }

    public String getDin() {
        return din;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public Integer getForecastHorizonDays() {
        return forecastHorizonDays;
    }

    public Integer getPredictedQuantity() {
        return predictedQuantity;
    }

    public ForecastConfidence getConfidence() {
        return confidence;
    }

    public BigDecimal getDaysOfSupply() {
        return daysOfSupply;
    }

    public ReorderStatus getReorderStatus() {
        return reorderStatus;
    }

    public String getModelPath() {
        return modelPath;
    }

    public BigDecimal getProphetLower() {
        return prophetLower;
    }

    public BigDecimal getProphetUpper() {
        return prophetUpper;
    }

    public BigDecimal getAvgDailyDemand() {
        return avgDailyDemand;
    }

    public BigDecimal getReorderPoint() {
        return reorderPoint;
    }

    public Integer getDataPointsUsed() {
        return dataPointsUsed;
    }

    public Boolean isOutdated() {
        return isOutdated;
    }

    public void setIsOutdated(Boolean isOutdated) {
        this.isOutdated = isOutdated;
    }
}
