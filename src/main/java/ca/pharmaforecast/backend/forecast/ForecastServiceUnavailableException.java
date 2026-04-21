package ca.pharmaforecast.backend.forecast;

public class ForecastServiceUnavailableException extends RuntimeException {

    public ForecastServiceUnavailableException(String message) {
        super(message);
    }

    public ForecastServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
