package ca.pharmaforecast.backend.drug;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DrugRepository extends JpaRepository<Drug, UUID> {
    Optional<Drug> findByDin(String din);

    List<Drug> findByDinIn(List<String> dins);

    List<Drug> findByLastRefreshedAtLessThanEqual(Instant staleCutoff);

    @Query("SELECT DISTINCT d FROM Drug d WHERE d.din IN (SELECT dr.din FROM DispensingRecord dr WHERE dr.locationId = :locationId)")
    List<Drug> findByLocationId(@Param("locationId") UUID locationId);
}
