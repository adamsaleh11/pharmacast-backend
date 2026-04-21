package ca.pharmaforecast.backend.common.exception;

public class InsufficientDataException extends RuntimeException {

    public InsufficientDataException() {
        super("Not enough historical data to generate a reliable forecast (minimum 14 data points required)");
    }
}