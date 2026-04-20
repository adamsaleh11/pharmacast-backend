package ca.pharmaforecast.backend.drug;

import ca.pharmaforecast.backend.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

import static lombok.AccessLevel.PROTECTED;

@Getter
@Setter
@Entity
@Table(name = "drugs")
@NoArgsConstructor(access = PROTECTED)
public class Drug extends BaseEntity {

    @Column(name = "din", nullable = false)
    private String din;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "strength", nullable = false)
    private String strength;

    @Column(name = "form", nullable = false)
    private String form;

    @Column(name = "therapeutic_class", nullable = false)
    private String therapeuticClass;

    @Column(name = "manufacturer", nullable = false)
    private String manufacturer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DrugStatus status;

    @Column(name = "last_refreshed_at", nullable = false)
    private Instant lastRefreshedAt;

    public String getDin() {
        return din;
    }

    public void setDin(String din) {
        this.din = din;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStrength() {
        return strength;
    }

    public void setStrength(String strength) {
        this.strength = strength;
    }

    public String getForm() {
        return form;
    }

    public void setForm(String form) {
        this.form = form;
    }

    public String getTherapeuticClass() {
        return therapeuticClass;
    }

    public void setTherapeuticClass(String therapeuticClass) {
        this.therapeuticClass = therapeuticClass;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public DrugStatus getStatus() {
        return status;
    }

    public void setStatus(DrugStatus status) {
        this.status = status;
    }

    public Instant getLastRefreshedAt() {
        return lastRefreshedAt;
    }

    public void setLastRefreshedAt(Instant lastRefreshedAt) {
        this.lastRefreshedAt = lastRefreshedAt;
    }
}
