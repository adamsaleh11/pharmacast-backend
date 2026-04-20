package ca.pharmaforecast.backend.drug;

import org.springframework.stereotype.Component;

@Component
public class DinNormalizer {

    public String normalize(String din) {
        if (din == null) {
            throw new InvalidDinException("DIN is required");
        }
        String trimmed = din.trim();
        if (trimmed.isEmpty() || trimmed.length() > 8 || !trimmed.matches("\\d+")) {
            throw new InvalidDinException("DIN must contain 1 to 8 numeric digits");
        }
        return "0".repeat(8 - trimmed.length()) + trimmed;
    }
}
