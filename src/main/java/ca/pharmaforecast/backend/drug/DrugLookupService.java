package ca.pharmaforecast.backend.drug;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class DrugLookupService {

    private final DrugRepository drugRepository;
    private final DinNormalizer dinNormalizer;

    public DrugLookupService(DrugRepository drugRepository, DinNormalizer dinNormalizer) {
        this.drugRepository = drugRepository;
        this.dinNormalizer = dinNormalizer;
    }

    public DrugResponse getByDin(String din) {
        String normalizedDin = dinNormalizer.normalize(din);
        return drugRepository.findByDin(normalizedDin)
                .map(DrugResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Drug not found"));
    }
}
