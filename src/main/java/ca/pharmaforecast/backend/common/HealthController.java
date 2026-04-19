package ca.pharmaforecast.backend.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;

@RestController
public class HealthController {

    private final Clock clock;

    public HealthController(Clock clock) {
        this.clock = clock;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("ok", Instant.now(clock));
    }

    public record HealthResponse(String status, Instant timestamp) {
    }
}
