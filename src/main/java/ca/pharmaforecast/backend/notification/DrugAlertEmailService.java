package ca.pharmaforecast.backend.notification;

import ca.pharmaforecast.backend.auth.User;
import ca.pharmaforecast.backend.drug.DrugStatus;
import ca.pharmaforecast.backend.location.Location;

import java.util.List;

public interface DrugAlertEmailService {

    void sendDrugDiscontinuedAlert(
            List<User> recipients,
            Location location,
            String din,
            String drugName,
            DrugStatus previousStatus,
            DrugStatus newStatus,
            int currentStockUnits
    );
}
