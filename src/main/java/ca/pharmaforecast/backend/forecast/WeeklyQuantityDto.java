package ca.pharmaforecast.backend.forecast;

public record WeeklyQuantityDto(
        String week,
        Integer quantity
) {
}
