package ca.pharmaforecast.backend.upload;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class CsvUploadStatusConverter implements AttributeConverter<CsvUploadStatus, String> {

    @Override
    public String convertToDatabaseColumn(CsvUploadStatus attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.name();
    }

    @Override
    public CsvUploadStatus convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return switch (dbData.toUpperCase()) {
            case "PENDING" -> CsvUploadStatus.PENDING;
            case "PROCESSING" -> CsvUploadStatus.PROCESSING;
            case "SUCCESS" -> CsvUploadStatus.SUCCESS;
            case "ERROR" -> CsvUploadStatus.ERROR;
            default -> throw new IllegalArgumentException("Unknown csv upload status: " + dbData);
        };
    }
}
