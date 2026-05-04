package ca.pharmaforecast.backend.dispensing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DispensingRecordRepository extends JpaRepository<DispensingRecord, UUID> {
    Optional<DispensingRecord> findTopByLocationIdAndDinOrderByDispensedDateDesc(UUID locationId, String din);
    List<DispensingRecord> findByLocationId(UUID locationId);

    @Query("select distinct record.locationId from DispensingRecord record where record.din = :din")
    List<UUID> findDistinctLocationIdsByDin(@Param("din") String din);

    List<DispensingRecord> findByLocationIdAndDin(UUID locationId, String din);

    List<DispensingRecord> findByDin(String din);

    @Query("select distinct record.din from DispensingRecord record where record.locationId = :locationId")
    List<String> findDistinctDinByLocationId(@Param("locationId") UUID locationId);

    List<DispensingRecord> findByLocationIdAndDispensedDateBetween(UUID locationId, LocalDate start, LocalDate end);
}
