package ca.pharmaforecast.backend.forecast;

import ca.pharmaforecast.backend.drug.Drug;
import ca.pharmaforecast.backend.drug.DrugRepository;
import ca.pharmaforecast.backend.currentstock.CurrentStockRepository;
import ca.pharmaforecast.backend.currentstock.CurrentStock;
import ca.pharmaforecast.backend.currentstock.CurrentStock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ForecastReadService {

    private final ForecastRepository forecastRepository;
    private final DrugRepository drugRepository;
    private final DrugThresholdRepository drugThresholdRepository;
    private final CurrentStockRepository currentStockRepository;

    public ForecastReadService(
            ForecastRepository forecastRepository,
            DrugRepository drugRepository,
            DrugThresholdRepository drugThresholdRepository,
            CurrentStockRepository currentStockRepository
    ) {
        this.forecastRepository = forecastRepository;
        this.drugRepository = drugRepository;
        this.drugThresholdRepository = drugThresholdRepository;
        this.currentStockRepository = currentStockRepository;
    }

    public List<ForecastSummaryDto> getLatestForecasts(UUID locationId, ForecastQueryParams params) {
        List<Forecast> latest = forecastRepository.findByLocationIdOrderByGeneratedAtDesc(locationId).stream()
                .collect(Collectors.groupingBy(Forecast::getDin, Collectors.collectingAndThen(
                        Collectors.maxBy(Comparator.comparing(Forecast::getGeneratedAt)),
                        forecast -> forecast.orElse(null)
                )))
                .values()
                .stream()
                .filter(forecast -> forecast != null)
                .filter(forecast -> params.horizonDays() == null || params.horizonDays().equals(forecast.getForecastHorizonDays()))
                .filter(forecast -> params.status() == null || params.status().equalsIgnoreCase(forecast.getReorderStatus().name()))
                .sorted(sorter(params))
                .toList();

        Map<String, Drug> drugsByDin = drugRepository.findByDinIn(latest.stream().map(Forecast::getDin).toList())
                .stream()
                .collect(Collectors.toMap(Drug::getDin, drug -> drug));
        Map<String, ForecastThresholdDto> thresholdsByDin = latest.stream()
                .map(Forecast::getDin)
                .distinct()
                .collect(Collectors.toMap(din -> din, din -> drugThresholdRepository.findByLocationIdAndDin(locationId, din)
                        .map(value -> new ForecastThresholdDto(value.getLeadTimeDays(), value.getSafetyMultiplier().value()))
                        .orElse(new ForecastThresholdDto(2, 1.0))));

        return latest.stream()
                .filter(forecast -> matchesSearch(params.search(), forecast, drugsByDin))
                .map(forecast -> new ForecastSummaryDto(
                        forecast.getDin(),
                        drugName(drugsByDin, forecast.getDin()),
                        strength(drugsByDin, forecast.getDin()),
                        forecast.getPredictedQuantity(),
                        forecast.getConfidence().name().toUpperCase(),
                        forecast.getDaysOfSupply() == null ? null : forecast.getDaysOfSupply().doubleValue(),
                        forecast.getReorderStatus().name().toUpperCase(),
                        forecast.getGeneratedAt(),
                        currentStockRepository.findByLocationIdAndDin(locationId, forecast.getDin())
                                .map(CurrentStock::getQuantity)
                                .orElse(null),
                        currentStockRepository.findByLocationIdAndDin(locationId, forecast.getDin()).isPresent(),
                        thresholdsByDin.get(forecast.getDin())
                ))
                .toList();
    }

    private Comparator<Forecast> sorter(ForecastQueryParams params) {
        Comparator<Forecast> comparator = Comparator.comparing(Forecast::getGeneratedAt).reversed();
        if ("predicted_quantity".equalsIgnoreCase(params.sort())) {
            comparator = Comparator.comparing(Forecast::getPredictedQuantity).reversed();
        }
        if ("asc".equalsIgnoreCase(params.order())) {
            comparator = comparator.reversed();
        }
        return comparator;
    }

    private boolean matchesSearch(String search, Forecast forecast, Map<String, Drug> drugsByDin) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String normalized = search.toLowerCase();
        if (forecast.getDin().contains(search)) {
            return true;
        }
        Drug drug = drugsByDin.get(forecast.getDin());
        return drug != null && drug.getName().toLowerCase().contains(normalized);
    }

    private String drugName(Map<String, Drug> drugsByDin, String din) {
        Drug drug = drugsByDin.get(din);
        return drug == null ? null : drug.getName();
    }

    private String strength(Map<String, Drug> drugsByDin, String din) {
        Drug drug = drugsByDin.get(din);
        return drug == null ? null : drug.getStrength();
    }
}
