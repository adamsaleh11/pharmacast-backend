package ca.pharmaforecast.backend.forecast;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class ForecastConfidenceConverter implements AttributeConverter<ForecastConfidence, String> {

    @Override
    public String convertToDatabaseColumn(ForecastConfidence attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.name();
    }

    @Override
    public ForecastConfidence convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return ForecastConfidence.valueOf(dbData.toLowerCase());
    }
}
