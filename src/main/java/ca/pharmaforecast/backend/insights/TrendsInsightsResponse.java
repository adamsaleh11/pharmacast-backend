package ca.pharmaforecast.backend.insights;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

public record TrendsInsightsResponse(
        @JsonProperty("top_growing")
        List<DemandChange> topGrowing,
        @JsonProperty("top_declining")
        List<DemandChange> topDeclining,
        @JsonProperty("seasonal_peaks")
        List<SeasonalPeak> seasonalPeaks,
        @JsonProperty("total_dispensing_trend")
        List<WeeklyTotal> totalDispensingTrend
) {
    public record DemandChange(
            String din,
            @JsonProperty("drug_name")
            String drugName,
            @JsonProperty("growth_pct")
            Double growthPct,
            @JsonProperty("decline_pct")
            Double declinePct,
            @JsonProperty("weekly_trend")
            List<Integer> weeklyTrend
    ) {
    }

    public record SeasonalPeak(
            String din,
            @JsonProperty("drug_name")
            String drugName,
            @JsonProperty("peak_month")
            int peakMonth,
            @JsonProperty("avg_peak_demand")
            double avgPeakDemand
    ) {
    }

    public record WeeklyTotal(
            LocalDate week,
            @JsonProperty("total_quantity")
            int totalQuantity
    ) {
    }
}
