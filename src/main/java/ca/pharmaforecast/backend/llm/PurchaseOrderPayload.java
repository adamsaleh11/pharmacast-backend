package ca.pharmaforecast.backend.llm;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record PurchaseOrderPayload(
        @JsonProperty("pharmacy_name")
        String pharmacyName,
        @JsonProperty("location_address")
        String locationAddress,
        String today,
        @JsonProperty("horizon_days")
        int horizonDays,
        List<PurchaseOrderDrugPayload> drugs,
        @JsonProperty("max_tokens")
        int maxTokens
) {
}
