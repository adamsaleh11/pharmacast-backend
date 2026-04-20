package ca.pharmaforecast.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByIdAndEmail(UUID id, String email);

    List<User> findByOrganizationIdAndRoleIn(UUID organizationId, List<UserRole> roles);
}
