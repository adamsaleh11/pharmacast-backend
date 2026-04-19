package ca.pharmaforecast.backend.auth;

import java.security.Principal;
import java.util.UUID;

public record AuthenticatedUserPrincipal(
        UUID id,
        String email,
        UUID organizationId,
        UserRole role
) implements Principal {

    @Override
    public String getName() {
        return id.toString();
    }
}
