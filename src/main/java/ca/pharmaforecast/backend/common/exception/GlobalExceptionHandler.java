package ca.pharmaforecast.backend.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(StockNotSetException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    Map<String, String> stockNotSet(StockNotSetException ex) {
        return Map.of(
                "error", "STOCK_NOT_SET",
                "din", ex.getDin(),
                "message", "Enter current stock for %s before generating a forecast".formatted(ex.getDin())
        );
    }

    @ExceptionHandler(NoStockEnteredException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    Map<String, String> noStockEntered(NoStockEnteredException ex) {
        return Map.of(
                "error", "NO_STOCK_ENTERED",
                "message", ex.getMessage()
        );
    }
}
