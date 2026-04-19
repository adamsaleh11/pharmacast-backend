package ca.pharmaforecast.backend.organization;

import ca.pharmaforecast.backend.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

import static lombok.AccessLevel.PROTECTED;

@Getter
@Setter
@Entity
@Table(name = "organizations")
@NoArgsConstructor(access = PROTECTED)
public class Organization extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    @Column(name = "subscription_status")
    private String subscriptionStatus;

    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;
}
