package ca.pharmaforecast.backend.forecast;

import java.util.List;

public record NotificationCheckResult(
        List<NotificationAlert> alerts
) {
    public record NotificationAlert(
            String din,
            String reorderStatus,
            Double daysOfSupply,
            Integer predictedQuantity
    ) {
    }
}
