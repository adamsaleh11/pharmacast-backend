package ca.pharmaforecast.backend;

import ca.pharmaforecast.backend.auth.User;
import ca.pharmaforecast.backend.auth.UserRepository;
import ca.pharmaforecast.backend.location.Location;
import ca.pharmaforecast.backend.location.LocationRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@TestConfiguration
class AuthTestRepositoryConfig {

    static final Map<UserKey, User> USERS = new HashMap<>();
    static final Map<UUID, List<Location>> LOCATIONS = new HashMap<>();

    static void reset() {
        USERS.clear();
        LOCATIONS.clear();
    }

    @Bean
    UserRepository userRepository() {
        return (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(),
                new Class<?>[]{UserRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndEmail" -> Optional.ofNullable(USERS.get(new UserKey((UUID) args[0], (String) args[1])));
                    case "toString" -> "AuthTestUserRepository";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    @Bean
    LocationRepository locationRepository() {
        return (LocationRepository) Proxy.newProxyInstance(
                LocationRepository.class.getClassLoader(),
                new Class<?>[]{LocationRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByOrganizationIdAndDeactivatedAtIsNullOrderByNameAsc" ->
                            LOCATIONS.getOrDefault((UUID) args[0], List.of());
                    case "toString" -> "AuthTestLocationRepository";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    static void putUser(UUID id, String email, User user) {
        USERS.put(new UserKey(id, email), user);
    }

    static void putLocations(UUID organizationId, List<Location> locations) {
        LOCATIONS.put(organizationId, new ArrayList<>(locations));
    }

    private record UserKey(UUID id, String email) {
    }
}
