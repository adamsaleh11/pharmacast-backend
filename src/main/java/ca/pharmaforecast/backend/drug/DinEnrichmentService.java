package ca.pharmaforecast.backend.drug;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DinEnrichmentService {

    @Async
    public void enrich(List<String> dins) {
        // Health Canada enrichment is implemented behind this async boundary in a later slice.
    }
}
