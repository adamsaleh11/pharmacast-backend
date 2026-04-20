package ca.pharmaforecast.backend.drug;

public class HealthCanadaApiException extends RuntimeException {

    public HealthCanadaApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
