package ca.pharmaforecast.backend.forecast;

import ca.pharmaforecast.backend.auth.AuthenticatedUserPrincipal;
import ca.pharmaforecast.backend.auth.CurrentUserService;
import ca.pharmaforecast.backend.common.exception.NoStockEnteredException;
import ca.pharmaforecast.backend.currentstock.CurrentStockRepository;
import ca.pharmaforecast.backend.location.Location;
import ca.pharmaforecast.backend.location.LocationRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;

@RestController
@RequestMapping("/locations/{locationId}/forecasts")
public class ForecastController {

    private final CurrentUserService currentUserService;
    private final LocationRepository locationRepository;
    private final CurrentStockRepository currentStockRepository;
    private final ForecastService forecastService;
    private final ForecastReadService forecastReadService;

    public ForecastController(
            CurrentUserService currentUserService,
            LocationRepository locationRepository,
            CurrentStockRepository currentStockRepository,
            ForecastService forecastService,
            ForecastReadService forecastReadService
    ) {
        this.currentUserService = currentUserService;
        this.locationRepository = locationRepository;
        this.currentStockRepository = currentStockRepository;
        this.forecastService = forecastService;
        this.forecastReadService = forecastReadService;
    }

    @PostMapping("/generate")
    public ForecastResult generate(
            @PathVariable UUID locationId,
            @Valid @RequestBody GenerateForecastRequest request
    ) {
        validateLocationOwnership(locationId);
        return forecastService.generateForecast(locationId, request.din(), request.horizonDays());
    }

    @PostMapping(value = "/generate-all", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generateAll(
            @PathVariable UUID locationId,
            @Valid @RequestBody BatchForecastRequest request
    ) {
        validateLocationOwnership(locationId);
        java.util.Map<String, Integer> stockMap = currentStockRepository.getStockMapForLocation(locationId);
        if (request.dins() == null || request.dins().stream().noneMatch(stockMap::containsKey)) {
            throw new NoStockEnteredException();
        }
        SseEmitter emitter = new SseEmitter(0L);
        CompletableFuture.runAsync(() -> forecastService.streamBatchForecast(locationId, request, event -> {
            try {
                emitter.send(event);
                if (event instanceof ForecastBatchEvent.Done) {
                    emitter.complete();
                }
            } catch (IOException ex) {
                emitter.completeWithError(ex);
            }
        }));
        return emitter;
    }

    @GetMapping
    public Object list(
            @PathVariable UUID locationId,
            ForecastQueryParams params
    ) {
        validateLocationOwnership(locationId);
        return forecastReadService.getLatestForecasts(locationId, params == null ? ForecastQueryParams.defaults() : params);
    }

    private void validateLocationOwnership(UUID locationId) {
        AuthenticatedUserPrincipal currentUser = currentUserService.requireCurrentUser();
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new AccessDeniedException("Location is not accessible"));
        if (!location.getOrganizationId().equals(currentUser.organizationId())) {
            throw new AccessDeniedException("Location is not accessible");
        }
    }

    public record GenerateForecastRequest(
            @NotBlank String din,
            @NotNull Integer horizon_days
    ) {
        public Integer horizonDays() {
            return horizon_days;
        }
    }
}
