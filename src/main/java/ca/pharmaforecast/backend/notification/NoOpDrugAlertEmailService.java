package ca.pharmaforecast.backend.notification;

import ca.pharmaforecast.backend.auth.User;
import ca.pharmaforecast.backend.drug.DrugStatus;
import ca.pharmaforecast.backend.location.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
class NoOpDrugAlertEmailService implements DrugAlertEmailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NoOpDrugAlertEmailService.class);

    @Override
    public void sendDrugDiscontinuedAlert(
            List<User> recipients,
            Location location,
            String din,
            String drugName,
            DrugStatus previousStatus,
            DrugStatus newStatus,
            int currentStockUnits
    ) {
        LOGGER.info(
                "Drug discontinued email deferred for DIN {} at location {}. Recipients={}, previousStatus={}, newStatus={}, currentStockUnits={}",
                din,
                location.getId(),
                recipients.size(),
                previousStatus,
                newStatus,
                currentStockUnits
        );
    }
}
