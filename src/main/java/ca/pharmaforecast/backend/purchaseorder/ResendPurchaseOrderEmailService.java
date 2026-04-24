package ca.pharmaforecast.backend.purchaseorder;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;

@Service
class ResendPurchaseOrderEmailService implements PurchaseOrderEmailService {

    private final RestClient restClient;
    private final String fromEmail;

    @Autowired
    ResendPurchaseOrderEmailService(
            @Value("${pharmaforecast.resend.api-key:}") String apiKey,
            @Value("${pharmaforecast.resend.from-email:}") String fromEmail
    ) {
        this(
                RestClient.builder()
                        .baseUrl("https://api.resend.com")
                        .defaultHeader("Authorization", "Bearer " + (apiKey == null ? "" : apiKey.trim()))
                        .build(),
                fromEmail
        );
    }

    ResendPurchaseOrderEmailService(RestClient restClient, String fromEmail) {
        this.restClient = restClient;
        this.fromEmail = fromEmail == null ? "" : fromEmail.trim();
    }

    @Override
    public void sendPurchaseOrder(
            String recipientEmail,
            String subject,
            String body,
            String attachmentFilename,
            byte[] attachmentBytes
    ) {
        if (!StringUtils.hasText(fromEmail)) {
            throw new IllegalStateException("Resend sender email is not configured");
        }
        if (!StringUtils.hasText(recipientEmail)) {
            throw new IllegalArgumentException("Recipient email is required");
        }
        if (attachmentBytes == null) {
            throw new IllegalArgumentException("Attachment bytes are required");
        }
        ResendEmailRequest request = new ResendEmailRequest(
                fromEmail,
                List.of(recipientEmail),
                subject,
                body,
                List.of(new Attachment(
                        attachmentFilename,
                        Base64.getEncoder().encodeToString(attachmentBytes)
                ))
        );
        restClient.post()
                .uri("/emails")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    private record ResendEmailRequest(
            String from,
            List<String> to,
            String subject,
            String text,
            List<Attachment> attachments
    ) {
    }

    private record Attachment(
            String filename,
            @JsonProperty("content")
            String content
    ) {
    }
}
