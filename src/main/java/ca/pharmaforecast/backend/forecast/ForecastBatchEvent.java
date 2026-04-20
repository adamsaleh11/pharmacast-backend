package ca.pharmaforecast.backend.forecast;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public sealed interface ForecastBatchEvent permits ForecastBatchEvent.Result, ForecastBatchEvent.Error, ForecastBatchEvent.Done {
    record Result(String din, ForecastResult forecast) implements ForecastBatchEvent {
    }

    record Error(String din, String error) implements ForecastBatchEvent {
    }

    record Done(
            @JsonProperty("done") boolean done,
            @JsonProperty("total") int total,
            @JsonProperty("succeeded") int succeeded,
            @JsonProperty("failed") int failed,
            @JsonProperty("skipped_no_stock") int skippedNoStock,
            @JsonProperty("skipped_dins") List<String> skippedDins
    ) implements ForecastBatchEvent {
    }
}
