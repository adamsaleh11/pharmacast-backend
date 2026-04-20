package ca.pharmaforecast.backend.dispensing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DispensingRecordRepository extends JpaRepository<DispensingRecord, UUID> {
    Optional<DispensingRecord> findTopByLocationIdAndDinOrderByDispensedDateDesc(UUID locationId, String din);
}
