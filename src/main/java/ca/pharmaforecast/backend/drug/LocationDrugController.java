package ca.pharmaforecast.backend.drug;

import ca.pharmaforecast.backend.auth.AuthenticatedUserPrincipal;
import ca.pharmaforecast.backend.auth.CurrentUserService;
import ca.pharmaforecast.backend.location.Location;
import ca.pharmaforecast.backend.location.LocationRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/locations/{locationId}/drugs")
public class LocationDrugController {

    private final DrugRepository drugRepository;
    private final CurrentUserService currentUserService;
    private final LocationRepository locationRepository;

    public LocationDrugController(
            DrugRepository drugRepository,
            CurrentUserService currentUserService,
            LocationRepository locationRepository
    ) {
        this.drugRepository = drugRepository;
        this.currentUserService = currentUserService;
        this.locationRepository = locationRepository;
    }

    @GetMapping
    public DrugListResponse listByLocation(@PathVariable UUID locationId) {
        validateLocationOwnership(locationId);
        List<Drug> drugs = drugRepository.findByLocationId(locationId);
        return new DrugListResponse(drugs.stream()
                .map(d -> new DrugSummaryDto(
                        d.getDin(),
                        d.getName(),
                        d.getStrength(),
                        d.getTherapeuticClass(),
                        d.getManufacturer(),
                        d.getStatus().name()
                ))
                .toList());
    }

    private void validateLocationOwnership(UUID locationId) {
        AuthenticatedUserPrincipal currentUser = currentUserService.requireCurrentUser();
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new AccessDeniedException("Location is not accessible"));
        if (!location.getOrganizationId().equals(currentUser.organizationId())) {
            throw new AccessDeniedException("Location is not accessible");
        }
    }

    public record DrugListResponse(List<DrugSummaryDto> drugs) {
    }

    public record DrugSummaryDto(
            String din,
            @JsonProperty("drug_name") String drugName,
            String strength,
            @JsonProperty("therapeutic_class") String therapeuticClass,
            String manufacturer,
            String status
    ) {
    }
}