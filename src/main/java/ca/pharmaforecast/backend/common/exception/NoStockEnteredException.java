package ca.pharmaforecast.backend.common.exception;

public class NoStockEnteredException extends RuntimeException {
    public NoStockEnteredException() {
        super("No drugs have current stock entered. Enter stock quantities before generating forecasts.");
    }
}
