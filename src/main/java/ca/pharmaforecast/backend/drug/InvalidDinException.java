package ca.pharmaforecast.backend.drug;

public class InvalidDinException extends RuntimeException {

    public InvalidDinException(String message) {
        super(message);
    }
}
