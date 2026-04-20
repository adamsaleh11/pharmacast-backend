package ca.pharmaforecast.backend.currentstock;

import ca.pharmaforecast.backend.auth.AuthenticatedUserPrincipal;
import ca.pharmaforecast.backend.auth.CurrentUserService;
import ca.pharmaforecast.backend.location.Location;
import ca.pharmaforecast.backend.location.LocationRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/locations/{locationId}/stock")
public class CurrentStockController {

    private final CurrentUserService currentUserService;
    private final LocationRepository locationRepository;
    private final CurrentStockService currentStockService;

    public CurrentStockController(
            CurrentUserService currentUserService,
            LocationRepository locationRepository,
            CurrentStockService currentStockService
    ) {
        this.currentUserService = currentUserService;
        this.locationRepository = locationRepository;
        this.currentStockService = currentStockService;
    }

    @PutMapping("/{din}")
    public CurrentStockResponse upsertOne(
            @PathVariable UUID locationId,
            @PathVariable String din,
            @Valid @RequestBody CurrentStockQuantityRequest request
    ) {
        validateLocationOwnership(locationId);
        return CurrentStockResponse.from(currentStockService.upsert(locationId, din, request.quantity()));
    }

    @PutMapping
    public BulkUpsertResponse upsertAll(
            @PathVariable UUID locationId,
            @Valid @RequestBody BulkCurrentStockRequest request
    ) {
        validateLocationOwnership(locationId);
        currentStockService.upsertAll(locationId, request.entries().stream()
                .map(entry -> new CurrentStockService.Entry(entry.din(), entry.quantity()))
                .toList());
        return new BulkUpsertResponse(request.entries().size());
    }

    @GetMapping
    public List<CurrentStockResponse> list(@PathVariable UUID locationId) {
        validateLocationOwnership(locationId);
        return currentStockService.list(locationId).stream().map(CurrentStockResponse::from).toList();
    }

    private void validateLocationOwnership(UUID locationId) {
        AuthenticatedUserPrincipal currentUser = currentUserService.requireCurrentUser();
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new AccessDeniedException("Location is not accessible"));
        if (!location.getOrganizationId().equals(currentUser.organizationId())) {
            throw new AccessDeniedException("Location is not accessible");
        }
    }

    public record CurrentStockQuantityRequest(@Min(0) int quantity) {
    }

    public record BulkCurrentStockRequest(@NotEmpty List<@Valid Entry> entries) {
        public record Entry(@NotBlank String din, @Min(0) int quantity) {
        }
    }

    public record CurrentStockResponse(String din, int quantity, Instant updated_at) {
        static CurrentStockResponse from(CurrentStock stock) {
            return new CurrentStockResponse(stock.getDin(), stock.getQuantity(), stock.getUpdatedAt().toInstant());
        }
    }

    public record BulkUpsertResponse(int updated) {
    }
}
