package ca.pharmaforecast.backend.drug;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record StockAdjustmentCreateRequest(
        @JsonProperty("adjustment_quantity")
        @NotNull
        Integer adjustmentQuantity,
        @NotBlank
        String note
) {
}
