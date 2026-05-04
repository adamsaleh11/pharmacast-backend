package ca.pharmaforecast.backend.notification;

import ca.pharmaforecast.backend.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

import static lombok.AccessLevel.PROTECTED;

@Getter
@Setter
@Entity
@Table(name = "notification_settings")
@NoArgsConstructor(access = PROTECTED)
public class NotificationSettings extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "daily_digest_enabled", nullable = false)
    private Boolean dailyDigestEnabled;

    @Column(name = "weekly_insights_enabled", nullable = false)
    private Boolean weeklyInsightsEnabled;

    @Column(name = "critical_alerts_enabled", nullable = false)
    private Boolean criticalAlertsEnabled;

    public UUID getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public Boolean getDailyDigestEnabled() {
        return dailyDigestEnabled;
    }

    public void setDailyDigestEnabled(Boolean dailyDigestEnabled) {
        this.dailyDigestEnabled = dailyDigestEnabled;
    }

    public Boolean getWeeklyInsightsEnabled() {
        return weeklyInsightsEnabled;
    }

    public void setWeeklyInsightsEnabled(Boolean weeklyInsightsEnabled) {
        this.weeklyInsightsEnabled = weeklyInsightsEnabled;
    }

    public Boolean getCriticalAlertsEnabled() {
        return criticalAlertsEnabled;
    }

    public void setCriticalAlertsEnabled(Boolean criticalAlertsEnabled) {
        this.criticalAlertsEnabled = criticalAlertsEnabled;
    }
}
