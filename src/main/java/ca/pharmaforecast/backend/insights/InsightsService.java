package ca.pharmaforecast.backend.insights;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class InsightsService {

    public Optional<BigDecimal> estimateMonthlySavings(UUID locationId) {
        return Optional.empty();
    }
}
