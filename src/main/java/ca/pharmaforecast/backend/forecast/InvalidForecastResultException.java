package ca.pharmaforecast.backend.forecast;

public class InvalidForecastResultException extends RuntimeException {

    public InvalidForecastResultException(String message) {
        super(message);
    }
}
