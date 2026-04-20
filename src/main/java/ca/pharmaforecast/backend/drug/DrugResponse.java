package ca.pharmaforecast.backend.drug;

import java.time.Instant;

public record DrugResponse(
        String din,
        String name,
        String strength,
        String form,
        String therapeuticClass,
        String manufacturer,
        DrugStatus status,
        Instant lastRefreshedAt
) {
    static DrugResponse from(Drug drug) {
        return new DrugResponse(
                drug.getDin(),
                drug.getName(),
                drug.getStrength(),
                drug.getForm(),
                drug.getTherapeuticClass(),
                drug.getManufacturer(),
                drug.getStatus(),
                drug.getLastRefreshedAt()
        );
    }
}
