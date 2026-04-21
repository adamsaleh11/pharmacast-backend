package ca.pharmaforecast.backend.forecast;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class ReorderStatusConverter implements AttributeConverter<ReorderStatus, String> {

    @Override
    public String convertToDatabaseColumn(ReorderStatus attribute) {
        if (attribute == null) {
            return null;
        }
        return switch (attribute) {
            case green -> "ok";
            case amber -> "amber";
            case red -> "red";
        };
    }

    @Override
    public ReorderStatus convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return switch (dbData.toLowerCase()) {
            case "ok" -> ReorderStatus.green;
            case "amber" -> ReorderStatus.amber;
            case "red" -> ReorderStatus.red;
            default -> throw new IllegalArgumentException("Unknown reorder status: " + dbData);
        };
    }
}
