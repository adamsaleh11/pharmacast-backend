package ca.pharmaforecast.backend.upload;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record BacktestDemandRow(
        @JsonProperty("dispensed_date")
        String dispensedDate,
        String din,
        @JsonProperty("quantity_dispensed")
        int quantityDispensed,
        @JsonProperty("cost_per_unit")
        BigDecimal costPerUnit
) {
}
