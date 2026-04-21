package ca.pharmaforecast.backend.drug;

public record StockAdjustmentResponse(
        DrugDetailResponse.StockAdjustmentDto adjustment
) {
}
