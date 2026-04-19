package ca.pharmaforecast.backend.auth;

import ca.pharmaforecast.backend.location.Location;
import ca.pharmaforecast.backend.location.LocationRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final CurrentUserService currentUserService;
    private final LocationRepository locationRepository;

    public AuthController(CurrentUserService currentUserService, LocationRepository locationRepository) {
        this.currentUserService = currentUserService;
        this.locationRepository = locationRepository;
    }

    @GetMapping("/me")
    public CurrentUserResponse me() {
        AuthenticatedUserPrincipal currentUser = currentUserService.requireCurrentUser();
        List<LocationResponse> locations = locationRepository
                .findByOrganizationIdAndDeactivatedAtIsNullOrderByNameAsc(currentUser.organizationId())
                .stream()
                .map(LocationResponse::from)
                .toList();

        return new CurrentUserResponse(
                currentUser.id(),
                currentUser.email(),
                currentUser.role(),
                currentUser.organizationId(),
                locations
        );
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        // Spring Boot is stateless and does not issue or revoke Supabase JWTs.
        // The frontend must call supabase.auth.signOut() and discard local session state.
    }

    public record CurrentUserResponse(
            UUID id,
            String email,
            UserRole role,
            @JsonProperty("organization_id")
            UUID organizationId,
            List<LocationResponse> locations
    ) {
    }

    public record LocationResponse(
            UUID id,
            String name,
            String address
    ) {
        static LocationResponse from(Location location) {
            return new LocationResponse(location.getId(), location.getName(), location.getAddress());
        }
    }
}
