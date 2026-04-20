package ca.pharmaforecast.backend.auth;

import java.util.UUID;

public interface AuthBootstrapService {

    BootstrapResult bootstrapFirstOwner(BootstrapCommand command);

    record BootstrapCommand(
            UUID authUserId,
            String email,
            String organizationName,
            String locationName,
            String locationAddress
    ) {
    }

    record BootstrapResult(
            UUID organizationId,
            UUID locationId,
            UUID userId
    ) {
    }
}
