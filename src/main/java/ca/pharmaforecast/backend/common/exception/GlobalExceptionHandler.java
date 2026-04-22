package ca.pharmaforecast.backend.common.exception;

import ca.pharmaforecast.backend.llm.ExplainStockNotSetException;
import ca.pharmaforecast.backend.llm.ForecastNotFoundException;
import ca.pharmaforecast.backend.llm.LlmUnavailableException;
import ca.pharmaforecast.backend.forecast.ForecastServiceUnavailableException;
import ca.pharmaforecast.backend.forecast.InvalidForecastResultException;
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

    @ExceptionHandler(InsufficientDataException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    Map<String, String> insufficientData(InsufficientDataException ex) {
        return Map.of(
                "error", "INSUFFICIENT_DATA",
                "message", ex.getMessage()
        );
    }

    @ExceptionHandler(ForecastServiceUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    Map<String, String> forecastServiceUnavailable(ForecastServiceUnavailableException ex) {
        return Map.of(
                "error", "FORECAST_SERVICE_UNAVAILABLE",
                "message", ex.getMessage()
        );
    }

    @ExceptionHandler(InvalidForecastResultException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    Map<String, String> invalidForecastResult(InvalidForecastResultException ex) {
        return Map.of(
                "error", "INVALID_FORECAST_RESULT",
                "message", ex.getMessage()
        );
    }

    @ExceptionHandler(ForecastNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    Map<String, String> forecastNotFound(ForecastNotFoundException ex) {
        return Map.of(
                "error", "FORECAST_NOT_FOUND",
                "message", "Generate a forecast first"
        );
    }

    @ExceptionHandler(ExplainStockNotSetException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    Map<String, String> explainStockNotSet(ExplainStockNotSetException ex) {
        return Map.of(
                "error", "STOCK_NOT_SET",
                "message", "Enter current stock before explaining"
        );
    }

    @ExceptionHandler(LlmUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    Map<String, String> llmUnavailable(LlmUnavailableException ex) {
        return Map.of(
                "error", "LLM_UNAVAILABLE",
                "message", "Try again in a moment"
        );
    }
}
