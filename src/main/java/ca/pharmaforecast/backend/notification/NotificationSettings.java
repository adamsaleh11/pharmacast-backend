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
}
