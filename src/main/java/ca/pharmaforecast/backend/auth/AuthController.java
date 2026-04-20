package ca.pharmaforecast.backend.auth;

import ca.pharmaforecast.backend.location.Location;
import ca.pharmaforecast.backend.location.LocationRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final AuthBootstrapService authBootstrapService;

    public AuthController(
            CurrentUserService currentUserService,
            LocationRepository locationRepository,
            AuthBootstrapService authBootstrapService
    ) {
        this.currentUserService = currentUserService;
        this.locationRepository = locationRepository;
        this.authBootstrapService = authBootstrapService;
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

    @PostMapping("/bootstrap")
    public BootstrapResponse bootstrap(@Valid @RequestBody BootstrapRequest request, Authentication authentication) {
        Jwt jwt = requireJwt(authentication);
        UUID authUserId = parseSubject(jwt.getSubject());
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            email = jwt.getClaimAsString("user_email");
        }
        if (email == null || email.isBlank()) {
            throw new AuthenticationCredentialsNotFoundException("JWT email claim is missing. Ensure Supabase JWT includes email claim.");
        }

        AuthBootstrapService.BootstrapResult result = authBootstrapService.bootstrapFirstOwner(
                new AuthBootstrapService.BootstrapCommand(
                        authUserId,
                        email,
                        request.organizationName(),
                        request.locationName(),
                        request.locationAddress()
                )
        );
        return new BootstrapResponse(result.organizationId(), result.locationId(), result.userId());
    }

    private Jwt requireJwt(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return jwtAuthenticationToken.getToken();
        }
        throw new AuthenticationCredentialsNotFoundException("Supabase JWT is required for bootstrap");
    }

    private UUID parseSubject(String subject) {
        if (subject == null || subject.isBlank()) {
            throw new AuthenticationCredentialsNotFoundException("JWT subject is required for bootstrap");
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException ex) {
            throw new AuthenticationCredentialsNotFoundException("JWT subject must be a UUID");
        }
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

    public record BootstrapRequest(
            @JsonProperty("organization_name")
            @NotBlank
            String organizationName,

            @JsonProperty("location_name")
            @NotBlank
            String locationName,

            @JsonProperty("location_address")
            @NotBlank
            String locationAddress
    ) {
    }

    public record BootstrapResponse(
            @JsonProperty("organization_id")
            UUID organizationId,

            @JsonProperty("location_id")
            UUID locationId,

            @JsonProperty("user_id")
            UUID userId
    ) {
    }
}
