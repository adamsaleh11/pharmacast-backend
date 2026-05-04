package ca.pharmaforecast.backend.location;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LocationRepository extends JpaRepository<Location, UUID> {
    List<Location> findByOrganizationIdAndDeactivatedAtIsNullOrderByNameAsc(UUID organizationId);

    List<Location> findByDeactivatedAtIsNullOrderByNameAsc();
}
