package ca.pharmaforecast.backend.upload;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CsvUploadStatusConverterTest {

    private final CsvUploadStatusConverter converter = new CsvUploadStatusConverter();

    @Test
    void convertsCsvUploadStatusesToDatabaseValuesExpectedByCsvUploadTableConstraint() {
        assertThat(converter.convertToDatabaseColumn(CsvUploadStatus.PENDING)).isEqualTo("PENDING");
        assertThat(converter.convertToDatabaseColumn(CsvUploadStatus.PROCESSING)).isEqualTo("PROCESSING");
        assertThat(converter.convertToDatabaseColumn(CsvUploadStatus.SUCCESS)).isEqualTo("SUCCESS");
        assertThat(converter.convertToDatabaseColumn(CsvUploadStatus.ERROR)).isEqualTo("ERROR");
    }

    @Test
    void convertsDatabaseValuesBackToCsvUploadStatuses() {
        assertThat(converter.convertToEntityAttribute("PENDING")).isEqualTo(CsvUploadStatus.PENDING);
        assertThat(converter.convertToEntityAttribute("pending")).isEqualTo(CsvUploadStatus.PENDING);
        assertThat(converter.convertToEntityAttribute("PROCESSING")).isEqualTo(CsvUploadStatus.PROCESSING);
        assertThat(converter.convertToEntityAttribute("SUCCESS")).isEqualTo(CsvUploadStatus.SUCCESS);
        assertThat(converter.convertToEntityAttribute("ERROR")).isEqualTo(CsvUploadStatus.ERROR);
    }
}
