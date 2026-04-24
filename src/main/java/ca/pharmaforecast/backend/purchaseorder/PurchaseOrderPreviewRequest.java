package ca.pharmaforecast.backend.purchaseorder;

import com.fasterxml.jackson.annotation.JsonProperty;
import ca.pharmaforecast.backend.forecast.ReorderStatus;

import java.util.List;

public record PurchaseOrderPreviewRequest(
        List<String> dins,
        @JsonProperty("include_status")
        List<ReorderStatus> includeStatus
) {
}
