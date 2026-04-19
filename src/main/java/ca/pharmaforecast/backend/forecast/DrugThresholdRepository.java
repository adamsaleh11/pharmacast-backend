package ca.pharmaforecast.backend.forecast;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DrugThresholdRepository extends JpaRepository<DrugThreshold, UUID> {
}
