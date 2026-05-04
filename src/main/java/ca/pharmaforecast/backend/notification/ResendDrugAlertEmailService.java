package ca.pharmaforecast.backend.notification;

import ca.pharmaforecast.backend.auth.User;
import ca.pharmaforecast.backend.drug.DrugStatus;
import ca.pharmaforecast.backend.location.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
class ResendDrugAlertEmailService implements DrugAlertEmailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResendDrugAlertEmailService.class);
    private final ResendEmailService resendEmailService;

    ResendDrugAlertEmailService(ResendEmailService resendEmailService) {
        this.resendEmailService = resendEmailService;
    }

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
        String subject = "Health Canada alert — %s discontinued".formatted(drugName);
        String html = """
                <h1>Health Canada drug status alert</h1>
                <p><strong>%s</strong> at %s changed status from %s to %s.</p>
                <p>DIN: %s</p>
                <p>Current stock: %d units</p>
                """.formatted(
                escapeHtml(drugName),
                escapeHtml(location.getName()),
                previousStatus,
                newStatus,
                din,
                currentStockUnits
        );
        for (User recipient : recipients) {
            try {
                resendEmailService.sendEmail(recipient.getEmail(), subject, html);
            } catch (RuntimeException ex) {
                LOGGER.error("Drug discontinued email failed for DIN {} at location {}", din, location.getId(), ex);
            }
        }
    }

    private String escapeHtml(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
