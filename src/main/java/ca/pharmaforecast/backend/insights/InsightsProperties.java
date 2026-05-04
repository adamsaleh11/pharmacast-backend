package ca.pharmaforecast.backend.insights;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "pharmaforecast.insights")
public record InsightsProperties(
        BigDecimal stockoutValuePerDay
) {
    public InsightsProperties {
        if (stockoutValuePerDay == null) {
            stockoutValuePerDay = new BigDecimal("150");
        }
    }
}
