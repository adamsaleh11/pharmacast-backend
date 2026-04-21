package ca.pharmaforecast.backend.forecast;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReorderStatusConverterTest {

    private final ReorderStatusConverter converter = new ReorderStatusConverter();

    @Test
    void convertsGreenAmberRedToDatabaseValuesExpectedByForecastCheckConstraint() {
        assertThat(converter.convertToDatabaseColumn(ReorderStatus.green)).isEqualTo("ok");
        assertThat(converter.convertToDatabaseColumn(ReorderStatus.amber)).isEqualTo("amber");
        assertThat(converter.convertToDatabaseColumn(ReorderStatus.red)).isEqualTo("red");
    }

    @Test
    void convertsDatabaseValuesBackToEntityValues() {
        assertThat(converter.convertToEntityAttribute("ok")).isEqualTo(ReorderStatus.green);
        assertThat(converter.convertToEntityAttribute("amber")).isEqualTo(ReorderStatus.amber);
        assertThat(converter.convertToEntityAttribute("red")).isEqualTo(ReorderStatus.red);
    }
}
