package ca.pharmaforecast.backend.dispensing;

import ca.pharmaforecast.backend.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static lombok.AccessLevel.PROTECTED;

@Getter
@Setter
@Entity
@Table(name = "dispensing_records")
@NoArgsConstructor(access = PROTECTED)
public class DispensingRecord extends BaseEntity {

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(name = "din", nullable = false)
    private String din;

    @Column(name = "dispensed_date", nullable = false)
    private LocalDate dispensedDate;

    @Column(name = "quantity_dispensed", nullable = false)
    private Integer quantityDispensed;

    @Column(name = "quantity_on_hand")
    private Integer quantityOnHand;

    @Column(name = "cost_per_unit", precision = 12, scale = 4)
    private BigDecimal costPerUnit;

    @Column(name = "patient_id")
    private String patientId;

    public void setLocationId(UUID locationId) {
        this.locationId = locationId;
    }

    public void setDin(String din) {
        this.din = din;
    }

    public void setDispensedDate(LocalDate dispensedDate) {
        this.dispensedDate = dispensedDate;
    }

    public void setQuantityDispensed(Integer quantityDispensed) {
        this.quantityDispensed = quantityDispensed;
    }

    public UUID getLocationId() {
        return locationId;
    }

    public String getDin() {
        return din;
    }

    public LocalDate getDispensedDate() {
        return dispensedDate;
    }

    public Integer getQuantityDispensed() {
        return quantityDispensed;
    }

    public Integer getQuantityOnHand() {
        return quantityOnHand;
    }

    public void setQuantityOnHand(Integer quantityOnHand) {
        this.quantityOnHand = quantityOnHand;
    }

    public BigDecimal getCostPerUnit() {
        return costPerUnit;
    }
}
