package ca.pharmaforecast.backend.drug;

import ca.pharmaforecast.backend.auth.AuthenticatedUserPrincipal;
import ca.pharmaforecast.backend.auth.CurrentUserService;
import ca.pharmaforecast.backend.location.Location;
import ca.pharmaforecast.backend.location.LocationRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/locations/{locationId}/drugs")
public class LocationDrugController {

    private final DrugRepository drugRepository;
    private final CurrentUserService currentUserService;
    private final LocationRepository locationRepository;
    private final LocationDrugDetailService locationDrugDetailService;

    public LocationDrugController(
            DrugRepository drugRepository,
            CurrentUserService currentUserService,
            LocationRepository locationRepository,
            LocationDrugDetailService locationDrugDetailService
    ) {
        this.drugRepository = drugRepository;
        this.currentUserService = currentUserService;
        this.locationRepository = locationRepository;
        this.locationDrugDetailService = locationDrugDetailService;
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

    @GetMapping("/{din}/detail")
    public DrugDetailResponse detail(
            @PathVariable UUID locationId,
            @PathVariable String din
    ) {
        validateLocationOwnership(locationId);
        return locationDrugDetailService.getDetail(locationId, din);
    }

    @PutMapping("/{din}/threshold")
    public DrugDetailResponse.DrugThresholdDto upsertThreshold(
            @PathVariable UUID locationId,
            @PathVariable String din,
            @Valid @RequestBody DrugThresholdUpsertRequest request
    ) {
        validateLocationOwnership(locationId);
        return locationDrugDetailService.upsertThreshold(locationId, din, request);
    }

    @DeleteMapping("/{din}/threshold")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetThreshold(
            @PathVariable UUID locationId,
            @PathVariable String din
    ) {
        validateLocationOwnership(locationId);
        locationDrugDetailService.resetThreshold(locationId, din);
    }

    @PostMapping("/{din}/adjust")
    public StockAdjustmentResponse adjustStock(
            @PathVariable UUID locationId,
            @PathVariable String din,
            @Valid @RequestBody StockAdjustmentCreateRequest request
    ) {
        validateLocationOwnership(locationId);
        return new StockAdjustmentResponse(locationDrugDetailService.adjustStock(locationId, din, request));
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
