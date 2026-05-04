package ca.pharmaforecast.backend.currentstock;

import ca.pharmaforecast.backend.forecast.ForecastRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class CurrentStockService {

    private final CurrentStockRepository currentStockRepository;
    private final ForecastRepository forecastRepository;

    public CurrentStockService(CurrentStockRepository currentStockRepository, ForecastRepository forecastRepository) {
        this.currentStockRepository = currentStockRepository;
        this.forecastRepository = forecastRepository;
    }

    public CurrentStock upsert(UUID locationId, String din, int quantity) {
        Optional<CurrentStock> existingStock = currentStockRepository.findByLocationIdAndDin(locationId, din);
        CurrentStock stock = existingStock.orElseGet(CurrentStock::new);

        // Check if this is an update (not a new insert) and if quantity changed
        boolean isUpdate = existingStock.isPresent();
        boolean quantityChanged = isUpdate && !stock.getQuantity().equals(quantity);

        stock.setLocationId(locationId);
        stock.setDin(din);
        stock.setQuantity(quantity);
        CurrentStock saved = currentStockRepository.save(stock);

        // Mark all forecasts for this (location, din) as outdated if stock changed
        if (quantityChanged) {
            forecastRepository.markAsOutdatedByLocationAndDin(locationId, din);
        }

        return saved;
    }

    public List<CurrentStock> upsertAll(UUID locationId, List<Entry> entries) {
        return entries.stream()
                .map(entry -> upsert(locationId, entry.din(), entry.quantity()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CurrentStock> list(UUID locationId) {
        return currentStockRepository.findAllByLocationId(locationId).stream()
                .sorted(Comparator.comparing(CurrentStock::getDin))
                .toList();
    }

    public record Entry(String din, int quantity) {
    }
}
