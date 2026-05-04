package ca.pharmaforecast.backend.insights;

import ca.pharmaforecast.backend.auth.AuthenticatedUserPrincipal;
import ca.pharmaforecast.backend.auth.CurrentUserService;
import ca.pharmaforecast.backend.location.Location;
import ca.pharmaforecast.backend.location.LocationRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/locations/{locationId}/insights")
public class InsightsController {

    private final CurrentUserService currentUserService;
    private final LocationRepository locationRepository;
    private final InsightsService insightsService;

    public InsightsController(
            CurrentUserService currentUserService,
            LocationRepository locationRepository,
            InsightsService insightsService
    ) {
        this.currentUserService = currentUserService;
        this.locationRepository = locationRepository;
        this.insightsService = insightsService;
    }

    @GetMapping("/savings")
    public SavingsInsightsResponse savings(
            @PathVariable UUID locationId,
            @RequestParam(defaultValue = "30") int period
    ) {
        validateLocationOwnership(locationId);
        return insightsService.calculateSavings(locationId, period);
    }

    @GetMapping("/accuracy")
    public AccuracyInsightsResponse accuracy(
            @PathVariable UUID locationId,
            @RequestParam(defaultValue = "30") int period
    ) {
        validateLocationOwnership(locationId);
        return insightsService.calculateAccuracy(locationId, period);
    }

    @GetMapping("/trends")
    public TrendsInsightsResponse trends(
            @PathVariable UUID locationId,
            @RequestParam(defaultValue = "90") int period
    ) {
        validateLocationOwnership(locationId);
        return insightsService.calculateTrends(locationId, period);
    }

    @GetMapping("/health-score")
    public HealthScoreResponse healthScore(@PathVariable UUID locationId) {
        validateLocationOwnership(locationId);
        return insightsService.calculateHealthScore(locationId);
    }

    private void validateLocationOwnership(UUID locationId) {
        AuthenticatedUserPrincipal currentUser = currentUserService.requireCurrentUser();
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new AccessDeniedException("Location is not accessible"));
        if (!location.getOrganizationId().equals(currentUser.organizationId())) {
            throw new AccessDeniedException("Location is not accessible");
        }
    }
}
