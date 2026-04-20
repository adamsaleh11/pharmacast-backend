package ca.pharmaforecast.backend.dispensing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record DailyDispensingRecord(
        UUID locationId,
        String din,
        LocalDate dispensedDate,
        int quantityDispensed,
        int quantityOnHand,
        BigDecimal costPerUnit
) {
}
