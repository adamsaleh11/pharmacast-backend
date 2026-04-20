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

import java.util.UUID;

import static lombok.AccessLevel.PROTECTED;

@Getter
@Setter
@Entity
@Table(name = "drug_thresholds")
@NoArgsConstructor(access = PROTECTED)
public class DrugThreshold extends BaseEntity {

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "din", nullable = false)
    private String din;

    @Column(name = "lead_time_days", nullable = false)
    private Integer leadTimeDays;

    @Column(name = "red_threshold_days", nullable = false)
    private Integer redThresholdDays;

    @Column(name = "amber_threshold_days", nullable = false)
    private Integer amberThresholdDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "safety_multiplier", nullable = false)
    private SafetyMultiplier safetyMultiplier;

    @Column(name = "notifications_enabled", nullable = false)
    private Boolean notificationsEnabled;

    public Integer getLeadTimeDays() {
        return leadTimeDays;
    }

    public SafetyMultiplier getSafetyMultiplier() {
        return safetyMultiplier;
    }
}
