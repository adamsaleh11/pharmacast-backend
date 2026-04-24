package ca.pharmaforecast.backend.purchaseorder;

import ca.pharmaforecast.backend.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

import static lombok.AccessLevel.PROTECTED;

@Getter
@Setter
@Entity
@Table(name = "purchase_orders")
@NoArgsConstructor(access = PROTECTED)
public class PurchaseOrder extends BaseEntity {

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "grok_output", nullable = false)
    private String grokOutput;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "line_items", columnDefinition = "jsonb")
    private String lineItems;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PurchaseOrderStatus status;

    public UUID getLocationId() {
        return locationId;
    }

    public void setLocationId(UUID locationId) {
        this.locationId = locationId;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    public String getGrokOutput() {
        return grokOutput;
    }

    public void setGrokOutput(String grokOutput) {
        this.grokOutput = grokOutput;
    }

    public String getLineItems() {
        return lineItems;
    }

    public void setLineItems(String lineItems) {
        this.lineItems = lineItems;
    }

    public PurchaseOrderStatus getStatus() {
        return status;
    }

    public void setStatus(PurchaseOrderStatus status) {
        this.status = status;
    }
}
