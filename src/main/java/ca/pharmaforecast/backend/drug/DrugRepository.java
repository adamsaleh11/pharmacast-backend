package ca.pharmaforecast.backend.drug;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DrugRepository extends JpaRepository<Drug, UUID> {
    Optional<Drug> findByDin(String din);

    List<Drug> findByDinIn(List<String> dins);

    List<Drug> findByLastRefreshedAtLessThanEqual(Instant staleCutoff);
}
