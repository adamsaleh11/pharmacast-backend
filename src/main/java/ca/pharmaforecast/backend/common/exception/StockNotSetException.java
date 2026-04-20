package ca.pharmaforecast.backend.common.exception;

public class StockNotSetException extends RuntimeException {
    private final String din;

    public StockNotSetException(String din) {
        super("Current stock not set for DIN: " + din);
        this.din = din;
    }

    public String getDin() {
        return din;
    }
}
