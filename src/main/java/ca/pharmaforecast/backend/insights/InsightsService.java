package ca.pharmaforecast.backend.insights;

import ca.pharmaforecast.backend.dispensing.DispensingRecord;
import ca.pharmaforecast.backend.dispensing.DispensingRecordRepository;
import ca.pharmaforecast.backend.drug.Drug;
import ca.pharmaforecast.backend.drug.DrugRepository;
import ca.pharmaforecast.backend.forecast.Forecast;
import ca.pharmaforecast.backend.forecast.ForecastRepository;
import ca.pharmaforecast.backend.forecast.ReorderStatus;
import ca.pharmaforecast.backend.forecast.StockAdjustmentRepository;
import ca.pharmaforecast.backend.upload.CsvUploadRepository;
import ca.pharmaforecast.backend.upload.CsvUploadStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Transactional(readOnly = true)
public class InsightsService {

    private final ForecastRepository forecastRepository;
    private final DispensingRecordRepository dispensingRecordRepository;
    private final StockAdjustmentRepository stockAdjustmentRepository;
    private final CsvUploadRepository csvUploadRepository;
    private final DrugRepository drugRepository;
    private final Clock clock;
    private final InsightsProperties properties;

    public InsightsService(
            ForecastRepository forecastRepository,
            DispensingRecordRepository dispensingRecordRepository,
            StockAdjustmentRepository stockAdjustmentRepository,
            CsvUploadRepository csvUploadRepository,
            DrugRepository drugRepository,
            Clock clock,
            InsightsProperties properties
    ) {
        this.forecastRepository = forecastRepository;
        this.dispensingRecordRepository = dispensingRecordRepository;
        this.stockAdjustmentRepository = stockAdjustmentRepository;
        this.csvUploadRepository = csvUploadRepository;
        this.drugRepository = drugRepository;
        this.clock = clock;
        this.properties = properties;
    }

    public Optional<BigDecimal> estimateMonthlySavings(UUID locationId) {
        return Optional.empty();
    }

    public SavingsInsightsResponse calculateSavings(UUID locationId, int periodDays) {
        validatePeriod(periodDays);

        LocalDate today = LocalDate.now(clock);
        LocalDate currentStart = today.minusDays(periodDays);
        LocalDate priorStart = currentStart.minusDays(periodDays);
        Instant currentStartInstant = currentStart.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant currentEndExclusive = today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        List<DispensingRecord> records = dispensingRecordRepository.findByLocationIdAndDispensedDateBetween(
                locationId,
                priorStart,
                today
        );
        Map<String, BigDecimal> averageCostByDin = averageCostByDin(records);

        BigDecimal overstock = calculateOverstockAvoided(locationId, currentStartInstant, currentEndExclusive, averageCostByDin);
        boolean requiresCostData = averageCostByDin.isEmpty();

        boolean requiresMultipleUploads = csvUploadRepository.countByLocationIdAndStatus(locationId, CsvUploadStatus.SUCCESS) < 2;
        BigDecimal waste = requiresMultipleUploads
                ? null
                : calculateWasteEliminated(records, averageCostByDin, priorStart, currentStart, today);

        long currentZeroDays = zeroStockDrugDays(records, currentStart, today);
        long priorZeroDays = zeroStockDrugDays(records, priorStart, currentStart.minusDays(1));
        int stockoutsPrevented = (int) Math.max(0, priorZeroDays - currentZeroDays);
        BigDecimal stockoutValue = properties.stockoutValuePerDay()
                .multiply(BigDecimal.valueOf(stockoutsPrevented))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal total = sumNonNull(overstock, waste, stockoutValue);
        boolean insufficientData = requiresCostData || requiresMultipleUploads || overstock == null;
        String message = insufficientData
                ? "Some savings categories require additional cost, upload, or forecast history data."
                : null;

        return new SavingsInsightsResponse(
                periodDays,
                total,
                new SavingsInsightsResponse.OverstockAvoided(overstock, requiresCostData),
                new SavingsInsightsResponse.WasteEliminated(waste, requiresMultipleUploads),
                new SavingsInsightsResponse.StockoutsPrevented(stockoutsPrevented, stockoutValue),
                insufficientData,
                message
        );
    }

    public AccuracyInsightsResponse calculateAccuracy(UUID locationId, int periodDays) {
        validatePeriod(periodDays);
        LocalDate today = LocalDate.now(clock);
        LocalDate periodStart = today.minusDays(periodDays);
        Instant start = periodStart.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        List<Forecast> forecasts = forecastRepository.findByLocationIdAndGeneratedAtBetweenOrderByGeneratedAtAsc(locationId, start, end);
        List<DispensingRecord> records = dispensingRecordRepository.findByLocationIdAndDispensedDateBetween(locationId, periodStart, today);
        Map<String, String> drugNames = drugNames(forecasts.stream().map(Forecast::getDin).distinct().toList());

        List<AccuracyPair> pairs = forecasts.stream()
                .map(forecast -> toAccuracyPair(forecast, records, today))
                .flatMap(Optional::stream)
                .toList();
        List<AccuracyInsightsResponse.DrugAccuracy> byDrug = pairs.stream()
                .collect(Collectors.groupingBy(AccuracyPair::din))
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue().size() >= 3)
                .map(entry -> {
                    List<AccuracyPair> drugPairs = entry.getValue();
                    double mape = drugPairs.stream().mapToDouble(AccuracyPair::mape).average().orElse(0.0);
                    int forecastQty = drugPairs.stream().mapToInt(AccuracyPair::forecastQty).sum();
                    int actualQty = drugPairs.stream().mapToInt(AccuracyPair::actualQty).sum();
                    return new AccuracyInsightsResponse.DrugAccuracy(
                            entry.getKey(),
                            drugNames.getOrDefault(entry.getKey(), entry.getKey()),
                            round(mape),
                            forecastQty,
                            actualQty
                    );
                })
                .sorted(Comparator.comparingDouble(AccuracyInsightsResponse.DrugAccuracy::mape).reversed())
                .toList();

        Double overallAccuracy = byDrug.isEmpty()
                ? null
                : round(Math.max(0.0, 100.0 - byDrug.stream().mapToDouble(AccuracyInsightsResponse.DrugAccuracy::mape).average().orElse(100.0)));
        return new AccuracyInsightsResponse(overallAccuracy, byDrug);
    }

    public TrendsInsightsResponse calculateTrends(UUID locationId, int periodDays) {
        validatePeriod(periodDays);
        LocalDate today = LocalDate.now(clock);
        LocalDate periodStart = today.minusDays(periodDays);
        LocalDate twelveWeekStart = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).minusWeeks(11);
        List<DispensingRecord> periodRecords = dispensingRecordRepository.findByLocationIdAndDispensedDateBetween(locationId, periodStart, today);
        List<DispensingRecord> allRecords = dispensingRecordRepository.findByLocationId(locationId);
        Map<String, String> drugNames = drugNames(periodRecords.stream().map(DispensingRecord::getDin).distinct().toList());

        LocalDate midpoint = periodStart.plusDays(periodDays / 2L);
        List<TrendsInsightsResponse.DemandChange> changes = periodRecords.stream()
                .collect(Collectors.groupingBy(DispensingRecord::getDin))
                .entrySet()
                .stream()
                .map(entry -> demandChange(entry.getKey(), drugNames.getOrDefault(entry.getKey(), entry.getKey()), entry.getValue(), periodStart, midpoint, today, twelveWeekStart))
                .toList();

        List<TrendsInsightsResponse.DemandChange> growing = changes.stream()
                .filter(change -> change.growthPct() != null && change.growthPct() > 0)
                .sorted(Comparator.comparing(TrendsInsightsResponse.DemandChange::growthPct).reversed())
                .limit(5)
                .toList();
        List<TrendsInsightsResponse.DemandChange> declining = changes.stream()
                .filter(change -> change.declinePct() != null && change.declinePct() > 0)
                .sorted(Comparator.comparing(TrendsInsightsResponse.DemandChange::declinePct).reversed())
                .limit(5)
                .toList();

        return new TrendsInsightsResponse(
                growing,
                declining,
                seasonalPeaks(allRecords, drugNames),
                totalWeeklyTrend(allRecords, twelveWeekStart)
        );
    }

    public HealthScoreResponse calculateHealthScore(UUID locationId) {
        List<Forecast> latest = latestForecastByDin(forecastRepository.findByLocationIdOrderByGeneratedAtDesc(locationId));
        int stockHealth = latest.isEmpty()
                ? 0
                : (int) Math.round((double) latest.stream().filter(forecast -> forecast.getReorderStatus() == ReorderStatus.green).count() / latest.size() * 40.0);
        AccuracyInsightsResponse accuracy = calculateAccuracy(locationId, 30);
        int accuracyScore = accuracy.overallAccuracyPct() == null ? 15 : (int) Math.round(accuracy.overallAccuracyPct() / 100.0 * 30.0);
        StockoutReduction reduction = stockoutReduction(locationId, 30);
        int stockoutReduction = reduction.hasPriorData()
                ? (int) Math.round((double) reduction.prevented() / Math.max(reduction.priorZeroDays(), 1) * 30.0)
                : 15;
        stockoutReduction = clamp(stockoutReduction, 0, 30);
        int score = clamp(stockHealth + accuracyScore + stockoutReduction, 0, 100);
        return new HealthScoreResponse(score, new HealthScoreResponse.Breakdown(stockHealth, accuracyScore, stockoutReduction));
    }

    private BigDecimal calculateOverstockAvoided(
            UUID locationId,
            Instant periodStart,
            Instant periodEndExclusive,
            Map<String, BigDecimal> averageCostByDin
    ) {
        if (averageCostByDin.isEmpty()) {
            return null;
        }
        Map<String, Forecast> recommendationByDin = forecastRepository
                .findByLocationIdAndGeneratedAtLessThanEqualOrderByGeneratedAtDesc(locationId, periodStart)
                .stream()
                .collect(Collectors.toMap(Forecast::getDin, forecast -> forecast, (first, ignored) -> first));
        if (recommendationByDin.isEmpty()) {
            return null;
        }

        Map<String, Integer> orderedByDin = stockAdjustmentRepository
                .findByLocationIdAndAdjustedAtBetween(locationId, periodStart, periodEndExclusive)
                .stream()
                .filter(adjustment -> adjustment.getAdjustmentQuantity() != null && adjustment.getAdjustmentQuantity() > 0)
                .collect(Collectors.groupingBy(
                        adjustment -> adjustment.getDin(),
                        Collectors.summingInt(adjustment -> adjustment.getAdjustmentQuantity())
                ));

        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, Integer> entry : orderedByDin.entrySet()) {
            Forecast forecast = recommendationByDin.get(entry.getKey());
            BigDecimal averageCost = averageCostByDin.get(entry.getKey());
            if (forecast == null || averageCost == null) {
                continue;
            }
            int overstockUnits = entry.getValue() - forecast.getPredictedQuantity();
            if (overstockUnits > 0) {
                total = total.add(averageCost.multiply(BigDecimal.valueOf(overstockUnits)));
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private Optional<AccuracyPair> toAccuracyPair(Forecast forecast, List<DispensingRecord> records, LocalDate today) {
        LocalDate forecastDate = forecast.getGeneratedAt().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate actualEnd = forecastDate.plusDays(forecast.getForecastHorizonDays() - 1L);
        if (actualEnd.isAfter(today)) {
            return Optional.empty();
        }
        int actual = records.stream()
                .filter(record -> forecast.getDin().equals(record.getDin()))
                .filter(record -> !record.getDispensedDate().isBefore(forecastDate) && !record.getDispensedDate().isAfter(actualEnd))
                .mapToInt(DispensingRecord::getQuantityDispensed)
                .sum();
        double mape = Math.abs(actual - forecast.getPredictedQuantity()) / (double) Math.max(actual, 1) * 100.0;
        return Optional.of(new AccuracyPair(forecast.getDin(), forecast.getPredictedQuantity(), actual, mape));
    }

    private BigDecimal calculateWasteEliminated(
            List<DispensingRecord> records,
            Map<String, BigDecimal> averageCostByDin,
            LocalDate priorStart,
            LocalDate currentStart,
            LocalDate today
    ) {
        BigDecimal prior = plateauValue(records, averageCostByDin, priorStart, currentStart.minusDays(1));
        BigDecimal current = plateauValue(records, averageCostByDin, currentStart, today);
        BigDecimal eliminated = prior.subtract(current);
        if (eliminated.signum() < 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return eliminated.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal plateauValue(
            List<DispensingRecord> records,
            Map<String, BigDecimal> averageCostByDin,
            LocalDate start,
            LocalDate end
    ) {
        BigDecimal total = BigDecimal.ZERO;
        Map<String, List<DispensingRecord>> byDin = records.stream()
                .filter(record -> record.getQuantityOnHand() != null)
                .filter(record -> !record.getDispensedDate().isBefore(start) && !record.getDispensedDate().isAfter(end))
                .collect(Collectors.groupingBy(DispensingRecord::getDin));
        for (Map.Entry<String, List<DispensingRecord>> entry : byDin.entrySet()) {
            BigDecimal averageCost = averageCostByDin.get(entry.getKey());
            if (averageCost == null) {
                continue;
            }
            List<DispensingRecord> sorted = entry.getValue().stream()
                    .sorted(Comparator.comparing(DispensingRecord::getDispensedDate))
                    .toList();
            Integer plateauQuantity = null;
            LocalDate previousDate = null;
            int runLength = 0;
            boolean countedCurrentRun = false;
            for (DispensingRecord record : sorted) {
                boolean continuesRun = plateauQuantity != null
                        && plateauQuantity.equals(record.getQuantityOnHand())
                        && previousDate != null
                        && previousDate.plusDays(1).equals(record.getDispensedDate());
                if (!continuesRun) {
                    plateauQuantity = record.getQuantityOnHand();
                    runLength = 1;
                    countedCurrentRun = false;
                } else {
                    runLength++;
                }
                if (runLength >= 14 && !countedCurrentRun) {
                    total = total.add(averageCost.multiply(BigDecimal.valueOf(plateauQuantity)));
                    countedCurrentRun = true;
                }
                previousDate = record.getDispensedDate();
            }
        }
        return total;
    }

    private TrendsInsightsResponse.DemandChange demandChange(
            String din,
            String drugName,
            List<DispensingRecord> records,
            LocalDate periodStart,
            LocalDate midpoint,
            LocalDate today,
            LocalDate twelveWeekStart
    ) {
        int firstHalf = records.stream()
                .filter(record -> !record.getDispensedDate().isBefore(periodStart) && record.getDispensedDate().isBefore(midpoint))
                .mapToInt(DispensingRecord::getQuantityDispensed)
                .sum();
        int secondHalf = records.stream()
                .filter(record -> !record.getDispensedDate().isBefore(midpoint) && !record.getDispensedDate().isAfter(today))
                .mapToInt(DispensingRecord::getQuantityDispensed)
                .sum();
        double changePct = (secondHalf - firstHalf) / (double) Math.max(firstHalf, 1) * 100.0;
        Double growth = changePct > 0 ? round(changePct) : null;
        Double decline = changePct < 0 ? round(Math.abs(changePct)) : null;
        return new TrendsInsightsResponse.DemandChange(din, drugName, growth, decline, weeklyTrend(records, twelveWeekStart));
    }

    private List<Integer> weeklyTrend(List<DispensingRecord> records, LocalDate twelveWeekStart) {
        return IntStream.range(0, 12)
                .mapToObj(offset -> {
                    LocalDate start = twelveWeekStart.plusWeeks(offset);
                    LocalDate end = start.plusDays(6);
                    return records.stream()
                            .filter(record -> !record.getDispensedDate().isBefore(start) && !record.getDispensedDate().isAfter(end))
                            .mapToInt(DispensingRecord::getQuantityDispensed)
                            .sum();
                })
                .toList();
    }

    private List<TrendsInsightsResponse.WeeklyTotal> totalWeeklyTrend(List<DispensingRecord> records, LocalDate twelveWeekStart) {
        return IntStream.range(0, 12)
                .mapToObj(offset -> {
                    LocalDate start = twelveWeekStart.plusWeeks(offset);
                    LocalDate end = start.plusDays(6);
                    int total = records.stream()
                            .filter(record -> !record.getDispensedDate().isBefore(start) && !record.getDispensedDate().isAfter(end))
                            .mapToInt(DispensingRecord::getQuantityDispensed)
                            .sum();
                    return new TrendsInsightsResponse.WeeklyTotal(start, total);
                })
                .toList();
    }

    private List<TrendsInsightsResponse.SeasonalPeak> seasonalPeaks(List<DispensingRecord> records, Map<String, String> fallbackDrugNames) {
        long monthsCovered = records.stream()
                .map(record -> record.getDispensedDate().withDayOfMonth(1))
                .distinct()
                .count();
        if (monthsCovered < 6) {
            return List.of();
        }
        Map<String, String> names = drugNames(records.stream().map(DispensingRecord::getDin).distinct().toList());
        return records.stream()
                .collect(Collectors.groupingBy(DispensingRecord::getDin))
                .entrySet()
                .stream()
                .map(entry -> {
                    Map<Integer, Integer> totalsByMonth = entry.getValue().stream()
                            .collect(Collectors.groupingBy(record -> record.getDispensedDate().getMonthValue(), Collectors.summingInt(DispensingRecord::getQuantityDispensed)));
                    Map.Entry<Integer, Integer> peak = totalsByMonth.entrySet().stream()
                            .max(Map.Entry.comparingByValue())
                            .orElse(null);
                    if (peak == null) {
                        return null;
                    }
                    return new TrendsInsightsResponse.SeasonalPeak(
                            entry.getKey(),
                            names.getOrDefault(entry.getKey(), fallbackDrugNames.getOrDefault(entry.getKey(), entry.getKey())),
                            peak.getKey(),
                            round(peak.getValue())
                    );
                })
                .filter(java.util.Objects::nonNull)
                .limit(5)
                .toList();
    }

    private StockoutReduction stockoutReduction(UUID locationId, int periodDays) {
        LocalDate today = LocalDate.now(clock);
        LocalDate currentStart = today.minusDays(periodDays);
        LocalDate priorStart = currentStart.minusDays(periodDays);
        List<DispensingRecord> records = dispensingRecordRepository.findByLocationIdAndDispensedDateBetween(locationId, priorStart, today);
        long current = zeroStockDrugDays(records, currentStart, today);
        long prior = zeroStockDrugDays(records, priorStart, currentStart.minusDays(1));
        return new StockoutReduction((int) Math.max(0, prior - current), (int) prior, prior > 0);
    }

    private List<Forecast> latestForecastByDin(List<Forecast> forecasts) {
        return forecasts.stream()
                .collect(Collectors.toMap(Forecast::getDin, forecast -> forecast, (first, ignored) -> first))
                .values()
                .stream()
                .toList();
    }

    private Map<String, String> drugNames(List<String> dins) {
        if (dins.isEmpty()) {
            return Map.of();
        }
        return drugRepository.findByDinIn(dins).stream()
                .collect(Collectors.toMap(Drug::getDin, Drug::getName, (first, ignored) -> first));
    }

    private Map<String, BigDecimal> averageCostByDin(List<DispensingRecord> records) {
        return records.stream()
                .filter(record -> record.getCostPerUnit() != null)
                .collect(Collectors.groupingBy(
                        DispensingRecord::getDin,
                        Collectors.collectingAndThen(
                                Collectors.mapping(DispensingRecord::getCostPerUnit, Collectors.toList()),
                                costs -> costs.stream()
                                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                                        .divide(BigDecimal.valueOf(costs.size()), 4, RoundingMode.HALF_UP)
                        )
                ));
    }

    private long zeroStockDrugDays(List<DispensingRecord> records, LocalDate start, LocalDate endInclusive) {
        return records.stream()
                .filter(record -> record.getDispensedDate() != null)
                .filter(record -> !record.getDispensedDate().isBefore(start) && !record.getDispensedDate().isAfter(endInclusive))
                .filter(record -> Integer.valueOf(0).equals(record.getQuantityOnHand()))
                .map(record -> record.getDin() + "|" + record.getDispensedDate())
                .distinct()
                .count();
    }

    private BigDecimal sumNonNull(BigDecimal... values) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            if (value != null) {
                total = total.add(value);
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private void validatePeriod(int periodDays) {
        if (periodDays != 30 && periodDays != 60 && periodDays != 90) {
            throw new IllegalArgumentException("Insights period must be 30, 60, or 90 days");
        }
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record AccuracyPair(String din, int forecastQty, int actualQty, double mape) {
    }

    private record StockoutReduction(int prevented, int priorZeroDays, boolean hasPriorData) {
    }
}
