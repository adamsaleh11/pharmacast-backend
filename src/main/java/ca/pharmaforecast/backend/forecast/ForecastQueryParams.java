package ca.pharmaforecast.backend.forecast;

public record ForecastQueryParams(
        Integer horizonDays,
        String status,
        String search,
        String sort,
        String order
) {
    public static ForecastQueryParams defaults() {
        return new ForecastQueryParams(null, null, null, null, null);
    }
}
