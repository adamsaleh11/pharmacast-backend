package ca.pharmaforecast.backend.forecast;

import java.util.function.Consumer;

public interface ForecastBatchPort {
    void streamBatchForecast(BatchForecastRequest request, Consumer<ForecastBatchEvent> onEvent);
}
