package ca.pharmaforecast.backend.forecast;

import java.util.UUID;

public interface ForecastRequestAssembler {
    ForecastRequest build(UUID locationId, String din, Integer horizonDays);
}
