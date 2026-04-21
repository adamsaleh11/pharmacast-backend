package ca.pharmaforecast.backend.forecast;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ForecastConfidenceConverterTest {

    private final ForecastConfidenceConverter converter = new ForecastConfidenceConverter();

    @Test
    void convertsConfidenceToDatabaseTextValuesExpectedByForecastTableConstraint() {
        assertThat(converter.convertToDatabaseColumn(ForecastConfidence.low)).isEqualTo("low");
        assertThat(converter.convertToDatabaseColumn(ForecastConfidence.medium)).isEqualTo("medium");
        assertThat(converter.convertToDatabaseColumn(ForecastConfidence.high)).isEqualTo("high");
    }

    @Test
    void convertsDatabaseTextValuesBackToEntityEnumValues() {
        assertThat(converter.convertToEntityAttribute("low")).isEqualTo(ForecastConfidence.low);
        assertThat(converter.convertToEntityAttribute("medium")).isEqualTo(ForecastConfidence.medium);
        assertThat(converter.convertToEntityAttribute("high")).isEqualTo(ForecastConfidence.high);
    }
}
