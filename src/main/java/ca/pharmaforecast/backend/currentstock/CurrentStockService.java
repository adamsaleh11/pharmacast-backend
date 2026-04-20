package ca.pharmaforecast.backend.currentstock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CurrentStockService {

    private final CurrentStockRepository currentStockRepository;

    public CurrentStockService(CurrentStockRepository currentStockRepository) {
        this.currentStockRepository = currentStockRepository;
    }

    public CurrentStock upsert(UUID locationId, String din, int quantity) {
        CurrentStock stock = currentStockRepository.findByLocationIdAndDin(locationId, din).orElseGet(CurrentStock::new);
        stock.setLocationId(locationId);
        stock.setDin(din);
        stock.setQuantity(quantity);
        return currentStockRepository.save(stock);
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
